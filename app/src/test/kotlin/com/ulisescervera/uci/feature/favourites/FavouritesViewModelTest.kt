package com.ulisescervera.uci.feature.favourites

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.ObserveFavouritePropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import com.ulisescervera.uci.support.AppFixtures
import com.ulisescervera.uci.support.FakePropertyRepository
import com.ulisescervera.uci.support.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class FavouritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val propertyRepository = FakePropertyRepository(mapOf("1" to PropertyFlag.FAVOURITE))

    private fun viewModel() = FavouritesViewModel(
        observeFavouriteProperties = ObserveFavouritePropertiesUseCase(propertyRepository),
        toggleFavourite = ToggleFavouriteUseCase(propertyRepository),
        discardProperty = DiscardPropertyUseCase(propertyRepository),
    )

    @Test
    fun `loading is not the same as empty`() = runTest {
        val model = viewModel()

        // Before the first emission the screen must show a skeleton, not
        // "aún no tienes favoritos".
        assertThat(model.state.value.isLoading).isTrue()
        assertThat(model.state.value.isEmpty).isFalse()
    }

    @Test
    fun `an empty first emission is the empty state`() = runTest {
        val model = viewModel()

        propertyRepository.emitFavourites(emptyList())

        assertThat(model.state.value.isLoading).isFalse()
        assertThat(model.state.value.isEmpty).isTrue()
    }

    @Test
    fun `favourites arrive from the database`() = runTest {
        val model = viewModel()

        propertyRepository.emitFavourites(
            listOf(
                AppFixtures.property("1", flag = PropertyFlag.FAVOURITE),
                AppFixtures.property("2", flag = PropertyFlag.FAVOURITE),
            ),
        )

        assertThat(model.state.value.properties.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(model.state.value.isEmpty).isFalse()
    }

    @Test
    fun `un favouriting from here clears the flag`() = runTest {
        val model = viewModel()

        model.dispatch(FavouritesIntent.FavouriteToggled("1"))

        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.NONE)
    }

    @Test
    fun `discarding from here also removes it from favourites`() = runTest {
        val model = viewModel()

        model.dispatch(FavouritesIntent.DiscardClicked("1"))

        // One exclusive column: the row leaves both lists in one write.
        assertThat(propertyRepository.currentFlags()["1"]).isEqualTo(PropertyFlag.DISCARDED)
    }

    @Test
    fun `tapping a card opens the detail`() = runTest {
        val model = viewModel()

        model.effects.test {
            model.dispatch(FavouritesIntent.PropertyClicked("1"))

            assertThat(awaitItem()).isEqualTo(FavouritesEffect.OpenDetail("1"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
