package com.ulisescervera.uci.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.UciDatabase
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.local.entity.PropertyFlagEntity
import com.ulisescervera.uci.data.mapper.PropertyEntityMapper
import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.data.related.RelatedPropertiesService
import com.ulisescervera.uci.data.support.TestDispatcherProvider
import com.ulisescervera.uci.domain.common.getOrNull
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelatedPropertiesRepositoryImplTest {

    private lateinit var database: UciDatabase
    private val service = ScriptedService()

    private lateinit var repository: RelatedPropertiesRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UciDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = RelatedPropertiesRepositoryImpl(
            service = service,
            propertyDao = database.propertyDao(),
            entityMapper = PropertyEntityMapper(),
            errorMapper = ErrorMapper(),
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the relevance order from the service is preserved`() = runTest {
        seed("1", "2", "3")
        // SQLite would return these in primary-key order; the ranking must win.
        service.ids = listOf("3", "2")

        val related = repository.relatedTo("1").getOrNull()!!

        assertThat(related.map { it.id }).containsExactly("3", "2").inOrder()
    }

    @Test
    fun `ids the cache does not know about are skipped, not rendered as blanks`() = runTest {
        seed("1", "2")
        service.ids = listOf("2", "404")

        val related = repository.relatedTo("1").getOrNull()!!

        assertThat(related.map { it.id }).containsExactly("2")
    }

    @Test
    fun `related items arrive with their local flags already joined`() = runTest {
        seed("1", "2")
        database.propertyFlagDao().upsert(
            PropertyFlagEntity("2", PropertyFlag.FAVOURITE, 1_000L, "Europe/Madrid"),
        )
        service.ids = listOf("2")

        val related = repository.relatedTo("1").getOrNull()!!

        // Hydrating locally is what stops the related block from disagreeing
        // with the list above it.
        assertThat(related.single().isFavourite).isTrue()
        assertThat(related.single().favouriteMark).isNotNull()
    }

    @Test
    fun `a service failure becomes a UciError`() = runTest {
        service.failWith = java.io.IOException("no radio")

        val result = repository.relatedTo("1")

        assertThat(result.getOrNull()).isNull()
    }

    private suspend fun seed(vararg codes: String) {
        database.propertyDao().replaceFeed(
            codes.map { code ->
                PropertyEntity(
                    propertyCode = code,
                    orderInFeed = code.toIntOrNull() ?: 0,
                    propertyType = "flat",
                    operation = "sale",
                    street = null,
                    neighborhood = "Castellana",
                    district = null,
                    municipality = "Madrid",
                    province = "Madrid",
                    countryCode = "es",
                    latitude = null,
                    longitude = null,
                    priceAmount = 1_000_000.0,
                    priceCurrencySuffix = "€",
                    sizeSquareMeters = 100.0,
                    rooms = 3,
                    bathrooms = 2,
                    rawFloor = "2",
                    hasParkingSpace = null,
                    isParkingIncludedInPrice = null,
                    isExterior = null,
                    hasAirConditioning = null,
                    hasBoxRoom = null,
                    hasSwimmingPool = null,
                    hasTerrace = null,
                    hasGarden = null,
                    thumbnailUrl = null,
                    images = listOf(ImageRecord("https://example.test/$code.webp")),
                    summary = "",
                    cachedAtEpochMillis = 0L,
                )
            },
        )
    }

    private class ScriptedService : RelatedPropertiesService {
        var ids: List<String> = emptyList()
        var failWith: Throwable? = null

        override suspend fun relatedPropertyIds(propertyId: String, limit: Int): List<String> {
            failWith?.let { throw it }
            return ids
        }
    }
}
