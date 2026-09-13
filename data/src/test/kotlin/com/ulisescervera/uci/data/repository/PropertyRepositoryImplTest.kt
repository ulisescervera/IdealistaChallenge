package com.ulisescervera.uci.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.UciDatabase
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.data.mapper.PropertyDetailMapper
import com.ulisescervera.uci.data.mapper.PropertyDtoMapper
import com.ulisescervera.uci.data.mapper.PropertyEntityMapper
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.data.network.UciPropertyApi
import com.ulisescervera.uci.data.network.dto.PropertyDetailDto
import com.ulisescervera.uci.data.network.dto.PropertyListItemDto
import com.ulisescervera.uci.data.support.DtoFixtures
import com.ulisescervera.uci.data.support.FixedUciClock
import com.ulisescervera.uci.data.support.TestDispatcherProvider
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.PropertyFlag
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository, wired to a real in-memory database and a scripted API.
 *
 * This is the most valuable test file in `:data`. It exercises the whole
 * offline-first contract end to end -- DTO -> mapper -> Room -> mapper -> model
 * -- and it is the only place where "a favourite survives a refresh" and "an
 * error does not clear the cache" can actually be proven.
 */
@RunWith(RobolectricTestRunner::class)
class PropertyRepositoryImplTest {

    private lateinit var database: UciDatabase
    private lateinit var repository: PropertyRepositoryImpl
    private val api = ScriptedApi()
    private val clock = FixedUciClock()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = PropertyRepositoryImpl(
            api = api,
            propertyDao = database.propertyDao(),
            detailDao = database.propertyDetailDao(),
            flagStore = PropertyFlagStore(
                flagDao = database.propertyFlagDao(),
                clock = clock,
                errorMapper = ErrorMapper(),
                dispatchers = TestDispatcherProvider(),
            ),
            dtoMapper = PropertyDtoMapper(clock),
            entityMapper = PropertyEntityMapper(),
            detailMapper = PropertyDetailMapper(clock),
            errorMapper = ErrorMapper(),
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh populates the cache and the observable list`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"), DtoFixtures.rentalListItem())

        val result = repository.refreshProperties()

        assertThat(result).isInstanceOf(UciResult.Success::class.java)
        val properties = repository.observeVisibleProperties().first()
        assertThat(properties.map { it.id }).containsExactly("1", "2").inOrder()
    }

    @Test
    fun `the observable list is the single source of truth for flags`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        repository.refreshProperties()

        repository.observeVisibleProperties().test {
            assertThat(awaitItem().single().flag).isEqualTo(PropertyFlag.NONE)

            // Writing straight to the flags table simulates any other screen.
            database.propertyFlagDao().upsert(
                PropertyFlagEntity("1", PropertyFlag.FAVOURITE, 1_000L, "Europe/Madrid"),
            )

            assertThat(awaitItem().single().flag).isEqualTo(PropertyFlag.FAVOURITE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed refresh does not clear the cache`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        repository.refreshProperties()

        api.failWith = IOException("radio off")
        val result = repository.refreshProperties()

        assertThat(result).isEqualTo(UciResult.Failure(UciError.NoConnectivity))
        // Offline-first: the user keeps seeing what we already had.
        assertThat(repository.observeVisibleProperties().first()).hasSize(1)
    }

    @Test
    fun `a favourite survives a refresh`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        repository.refreshProperties()
        database.propertyFlagDao().upsert(
            PropertyFlagEntity("1", PropertyFlag.FAVOURITE, 1_000L, "Europe/Madrid"),
        )

        repository.refreshProperties()

        assertThat(repository.observeVisibleProperties().first().single().isFavourite).isTrue()
    }

    @Test
    fun `the detail is null until it has been fetched, and the summary is not`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        repository.refreshProperties()

        // This is what makes the detail screen render its header immediately
        // and its comment behind a skeleton.
        assertThat(repository.observePropertyDetail("1").first()).isNull()
        assertThat(repository.observeProperty("1").first()).isNotNull()
    }

    @Test
    fun `fetching the detail fills in the lift for the list too`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        api.detailResponse = DtoFixtures.detail(lift = true)
        repository.refreshProperties()

        // Before: the list endpoint never reports the lift.
        assertThat(repository.observeProperty("1").first()!!.hasLift).isNull()

        repository.refreshPropertyDetail("1")

        assertThat(repository.observeProperty("1").first()!!.hasLift).isTrue()
    }

    @Test
    fun `the detail keeps the garage from the cached summary`() = runTest {
        // detail.json has no parking information at all.
        api.listResponse = listOf(DtoFixtures.rentalListItem())
        api.detailResponse = DtoFixtures.detail()
        repository.refreshProperties()

        val result = repository.refreshPropertyDetail("2")

        val detail = (result as UciResult.Success).value
        assertThat(detail.summary.parkingSpace)
            .isEqualTo(ParkingSpace.Available(isIncludedInPrice = true))
    }

    @Test
    fun `the detail response is stored under the requested id`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"), DtoFixtures.listItem("3"))
        // The fixture always answers adid = 1, whichever property was asked for.
        api.detailResponse = DtoFixtures.detail(adId = 1)
        repository.refreshProperties()

        repository.refreshPropertyDetail("3")

        assertThat(repository.observePropertyDetail("3").first()?.id).isEqualTo("3")
    }

    @Test
    fun `fetching a detail for an uncached property is PropertyNotFound`() = runTest {
        api.detailResponse = DtoFixtures.detail()

        val result = repository.refreshPropertyDetail("99")

        // Not a generic failure: the UI should offer "volver al listado", not
        // "reintentar".
        assertThat(result).isEqualTo(UciResult.Failure(UciError.PropertyNotFound("99")))
    }

    @Test
    fun `discarded properties leave the visible list and appear in the projection`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"), DtoFixtures.rentalListItem())
        repository.refreshProperties()

        database.propertyFlagDao().upsert(
            PropertyFlagEntity("1", PropertyFlag.DISCARDED, 5_000L, "Europe/Madrid"),
        )

        assertThat(repository.observeVisibleProperties().first().map { it.id }).containsExactly("2")
        val discarded = repository.observeDiscardedProperties().first()
        assertThat(discarded.map { it.id }).containsExactly("1")
        assertThat(discarded.single().discardedAtEpochMillis).isEqualTo(5_000L)
    }

    @Test
    fun `the favourite mark is only attached to actual favourites`() = runTest {
        api.listResponse = listOf(DtoFixtures.listItem("1"))
        repository.refreshProperties()

        database.propertyFlagDao().upsert(
            PropertyFlagEntity("1", PropertyFlag.DISCARDED, 9_000L, "Europe/Madrid"),
        )

        // A discarded row also has an `updated_at`; it must not surface as a
        // favourite date.
        assertThat(repository.observeProperty("1").first()!!.favouriteMark).isNull()
    }

    @Test
    fun `raw floor codes become domain floors`() = runTest {
        api.listResponse = listOf(
            DtoFixtures.listItem("1", floor = "bj"),
            DtoFixtures.listItem("2", floor = "2"),
            DtoFixtures.listItem("3", floor = null),
        )
        repository.refreshProperties()

        val byId = repository.observeVisibleProperties().first().associateBy { it.id }
        assertThat(byId["1"]!!.floor).isEqualTo(Floor.Ground)
        assertThat(byId["2"]!!.floor).isEqualTo(Floor.Numbered(2))
        assertThat(byId["3"]!!.floor).isEqualTo(Floor.Missing)
    }

    @Test
    fun `a flag written through the repository shows up in the list it delegates to`() = runTest {
        // The point of merging the two interfaces: write on one side, read on
        // the other, no coordination in between. If the delegation to
        // PropertyFlagStore ever broke, only a test that crosses the seam
        // would notice -- the store's own tests would still pass.
        api.listResponse = listOf(DtoFixtures.listItem("1"), DtoFixtures.rentalListItem())
        repository.refreshProperties()

        repository.setFlag("1", PropertyFlag.DISCARDED)

        assertThat(repository.flagOf("1")).isEqualTo(PropertyFlag.DISCARDED)
        assertThat(repository.observeVisibleProperties().first().map { it.id })
            .containsExactly("2")
        assertThat(repository.observeDiscardedProperties().first().map { it.id })
            .containsExactly("1")

        assertThat(repository.restoreAllDiscarded()).isEqualTo(UciResult.Success(1))
        assertThat(repository.observeVisibleProperties().first()).hasSize(2)
    }

    /**
     * A hand-written stub rather than MockK: the two methods take no arguments,
     * so there is nothing to match on, and a plain class makes the failure
     * injection ([failWith]) obvious at the call site.
     */
    private class ScriptedApi : UciPropertyApi {
        var listResponse: List<PropertyListItemDto> = emptyList()
        var detailResponse: PropertyDetailDto = DtoFixtures.detail()
        var failWith: Throwable? = null

        override suspend fun properties(): List<PropertyListItemDto> {
            failWith?.let { throw it }
            return listResponse
        }

        override suspend fun propertyDetail(): PropertyDetailDto {
            failWith?.let { throw it }
            return detailResponse
        }
    }
}
