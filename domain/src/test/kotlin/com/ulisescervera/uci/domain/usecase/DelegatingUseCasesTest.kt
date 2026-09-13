package com.ulisescervera.uci.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.fake.PropertyFixtures
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.repository.PropertyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The seven use cases that only forward to [PropertyRepository].
 *
 * ### Why MockK here, and a hand-written fake everywhere else
 * These use cases have no state and no logic: the *only* thing worth asserting
 * is that each one calls the right repository method, with the right arguments,
 * and returns what it got back. That is a question about an interaction, which
 * is exactly what a mock is for, and a hand-written fake could not express it --
 * a fake returning the right list proves nothing about *which* method was
 * called.
 *
 * The contrast is deliberate. `FlagUseCasesTest` uses a fake because it asserts
 * behaviour over time ("toggle twice and you are back where you started"), and
 * a mock cannot express that. Same test suite, two tools, one reason each.
 *
 * The value of these tests is not the forwarding itself but the *wiring*: a
 * copy-paste slip that made `ObserveFavouritePropertiesUseCase` call
 * `observeVisibleProperties` would compile, type-check, and quietly show every
 * property in the favourites tab.
 */
class DelegatingUseCasesTest {

    private val repository = mockk<PropertyRepository>(relaxed = true)

    private val visible = listOf(PropertyFixtures.property(id = "1"))
    private val favourites = listOf(PropertyFixtures.property(id = "2"))
    private val discarded = listOf(PropertyFixtures.discarded(id = "3"))

    @Test
    fun `observing the visible list reads the visible query, not the favourites one`() = runTest {
        every { repository.observeVisibleProperties() } returns flowOf(visible)

        val emitted = ObserveVisiblePropertiesUseCase(repository)().first()

        assertThat(emitted).isEqualTo(visible)
        verify(exactly = 1) { repository.observeVisibleProperties() }
        verify(exactly = 0) { repository.observeFavouriteProperties() }
    }

    @Test
    fun `observing favourites reads the favourites query`() = runTest {
        every { repository.observeFavouriteProperties() } returns flowOf(favourites)

        val emitted: List<Property> = ObserveFavouritePropertiesUseCase(repository)().first()

        assertThat(emitted).isEqualTo(favourites)
        verify(exactly = 1) { repository.observeFavouriteProperties() }
        verify(exactly = 0) { repository.observeVisibleProperties() }
    }

    @Test
    fun `observing the discarded sheet reads the narrow projection`() = runTest {
        every { repository.observeDiscardedProperties() } returns flowOf(discarded)

        val emitted: List<DiscardedProperty> = ObserveDiscardedPropertiesUseCase(repository)().first()

        assertThat(emitted).isEqualTo(discarded)
        verify(exactly = 1) { repository.observeDiscardedProperties() }
    }

    @Test
    fun `observing one property forwards the id`() = runTest {
        every { repository.observeProperty("7") } returns flowOf(PropertyFixtures.property(id = "7"))

        val emitted = ObservePropertyUseCase(repository)("7").first()

        assertThat(emitted?.id).isEqualTo("7")
        verify(exactly = 1) { repository.observeProperty("7") }
    }

    @Test
    fun `observing one property propagates a cache miss as null`() = runTest {
        // The detail screen relies on this: `null` is what makes it render the
        // skeleton instead of an empty header.
        every { repository.observeProperty("7") } returns flowOf(null)

        assertThat(ObservePropertyUseCase(repository)("7").first()).isNull()
    }

    @Test
    fun `observing the detail forwards the id and propagates a cache miss`() = runTest {
        every { repository.observePropertyDetail("7") } returns flowOf(null)

        assertThat(ObservePropertyDetailUseCase(repository)("7").first()).isNull()
        verify(exactly = 1) { repository.observePropertyDetail("7") }
    }

    @Test
    fun `refreshing the list returns Unit, not the fetched data`() = runTest {
        coEvery { repository.refreshProperties() } returns UciResult.Success(Unit)

        val result = RefreshPropertiesUseCase(repository)()

        // Handing back the network list here would create a second, flag-less
        // source of truth; callers must read through the observe* use cases.
        assertThat(result).isEqualTo(UciResult.Success(Unit))
        coVerify(exactly = 1) { repository.refreshProperties() }
    }

    @Test
    fun `refreshing the list propagates a failure untouched`() = runTest {
        coEvery { repository.refreshProperties() } returns UciResult.Failure(UciError.NoConnectivity)

        assertThat(RefreshPropertiesUseCase(repository)())
            .isEqualTo(UciResult.Failure(UciError.NoConnectivity))
    }

    @Test
    fun `refreshing the detail forwards the id and returns the detail`() = runTest {
        val detail = mockk<PropertyDetail>()
        coEvery { repository.refreshPropertyDetail("7") } returns UciResult.Success(detail)

        val result = RefreshPropertyDetailUseCase(repository)("7")

        assertThat(result).isEqualTo(UciResult.Success(detail))
        coVerify(exactly = 1) { repository.refreshPropertyDetail("7") }
    }

    @Test
    fun `refreshing the detail propagates PropertyNotFound`() = runTest {
        coEvery { repository.refreshPropertyDetail("99") } returns
            UciResult.Failure(UciError.PropertyNotFound("99"))

        // Not a generic failure: the UI turns this into "volver al listado"
        // instead of a retry button that cannot work.
        assertThat(RefreshPropertyDetailUseCase(repository)("99"))
            .isEqualTo(UciResult.Failure(UciError.PropertyNotFound("99")))
    }
}
