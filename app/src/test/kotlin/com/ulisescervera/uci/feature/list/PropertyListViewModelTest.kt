package com.ulisescervera.uci.feature.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.ObserveDiscardedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ObserveVisiblePropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertiesUseCase
import com.ulisescervera.uci.domain.model.PropertyType
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import com.ulisescervera.uci.feature.list.filter.PropertyFilters
import com.ulisescervera.uci.support.AppFixtures
import com.ulisescervera.uci.support.FakePropertyRepository
import com.ulisescervera.uci.support.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The list ViewModel, driven through real use cases sitting on fake
 * repositories.
 *
 * Testing the intent -> use case -> repository path rather than mocking the use
 * cases means a ViewModel that dispatched to the *wrong* use case would fail
 * here. Mocks would let it pass.
 */
class PropertyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val propertyRepository = FakePropertyRepository()

    private fun viewModel() = PropertyListViewModel(
        observeVisibleProperties = ObserveVisiblePropertiesUseCase(propertyRepository),
        observeDiscardedProperties = ObserveDiscardedPropertiesUseCase(propertyRepository),
        refreshProperties = RefreshPropertiesUseCase(propertyRepository),
        toggleFavourite = ToggleFavouriteUseCase(propertyRepository),
        discardProperty = DiscardPropertyUseCase(propertyRepository),
        restoreProperty = RestorePropertyUseCase(propertyRepository),
    )

    // ------------------------------------------------------------- Surfaces

    @Test
    fun `the skeleton shows until the database has answered`() = runTest {
        val model = viewModel()

        // The very first state, before any emission.
        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Skeleton)
    }

    @Test
    fun `an empty first emission ends the skeleton and shows the empty state`() = runTest {
        val model = viewModel()

        propertyRepository.emitVisible(emptyList())

        // A finished query with no rows is "empty", not "still loading".
        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Empty)
    }

    @Test
    fun `properties produce the content surface`() = runTest {
        val model = viewModel()

        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Content)
        assertThat(model.state.value.properties).hasSize(1)
    }

    @Test
    fun `an empty list with discarded items gets its own copy`() = runTest {
        val model = viewModel()

        propertyRepository.emitVisible(emptyList())
        propertyRepository.emitDiscarded(listOf(AppFixtures.discarded("1")))

        // "Has descartado todos los inmuebles" reads very differently from
        // "no hay inmuebles".
        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.AllDiscarded)
    }

    @Test
    fun `a failed first load with an empty cache shows the error surface`() = runTest {
        propertyRepository.refreshListResult = UciResult.Failure(UciError.NoConnectivity)
        val model = viewModel()
        propertyRepository.emitVisible(emptyList())

        model.dispatch(PropertyListIntent.ScreenStarted)

        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Error)
        assertThat(model.state.value.error).isEqualTo(UciError.NoConnectivity)
    }

    @Test
    fun `cached content wins over a refresh failure`() = runTest {
        val model = viewModel()
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        propertyRepository.refreshListResult = UciResult.Failure(UciError.Timeout)

        model.effects.test {
            model.dispatch(PropertyListIntent.RefreshRequested)

            // Offline-first payoff: a snackbar, not an error screen.
            assertThat(awaitItem()).isEqualTo(PropertyListEffect.ShowError(UciError.Timeout))
            assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Content)
            assertThat(model.state.value.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --------------------------------------------------------------- Filters

    @Test
    fun `filters that exclude every cached property get their own surface`() = runTest {
        val model = viewModel()
        propertyRepository.emitVisible(listOf(AppFixtures.property("1", type = PropertyType.FLAT)))

        model.dispatch(
            PropertyListIntent.FiltersApplied(PropertyFilters(propertyType = PropertyType.CHALET)),
        )

        // There is content -- this is not the same as an empty feed -- it is
        // just all filtered out, which reads differently to the user.
        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.FilteredEmpty)
        assertThat(model.state.value.visibleProperties).isEmpty()
    }

    @Test
    fun `the filter button opens the sheet with the currently applied filters`() = runTest {
        val model = viewModel()
        val filters = PropertyFilters(requiresLift = true)
        model.dispatch(PropertyListIntent.FiltersApplied(filters))

        model.effects.test {
            model.dispatch(PropertyListIntent.FilterButtonClicked)

            assertThat(awaitItem()).isEqualTo(PropertyListEffect.OpenFiltersSheet(filters))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --------------------------------------------------------------- Refresh

    @Test
    fun `the automatic first refresh happens once per view model`() = runTest {
        val model = viewModel()

        // ScreenStarted fires on every onViewCreated: rotation, coming back
        // from the detail, switching tabs.
        model.dispatch(PropertyListIntent.ScreenStarted)
        model.dispatch(PropertyListIntent.ScreenStarted)
        model.dispatch(PropertyListIntent.ScreenStarted)

        assertThat(propertyRepository.refreshListCallCount).isEqualTo(1)
    }

    @Test
    fun `pull to refresh always refetches and shows the spinner`() = runTest {
        val model = viewModel()
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        model.dispatch(PropertyListIntent.RefreshRequested)
        model.dispatch(PropertyListIntent.RefreshRequested)

        assertThat(propertyRepository.refreshListCallCount).isEqualTo(2)
        // The spinner is cleared by the time the call returns.
        assertThat(model.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `retry clears the error before refetching`() = runTest {
        propertyRepository.refreshListResult = UciResult.Failure(UciError.NoConnectivity)
        val model = viewModel()
        propertyRepository.emitVisible(emptyList())
        model.dispatch(PropertyListIntent.ScreenStarted)

        propertyRepository.refreshListResult = UciResult.Success(Unit)
        propertyRepository.onRefreshEmit = listOf(AppFixtures.property("1"))
        model.dispatch(PropertyListIntent.RetryRequested)

        assertThat(model.state.value.error).isNull()
        assertThat(model.state.value.surface).isEqualTo(PropertyListUiState.Surface.Content)
    }

    // ----------------------------------------------------------------- Flags

    @Test
    fun `toggling a favourite writes through the use case`() = runTest {
        val model = viewModel()
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        model.dispatch(PropertyListIntent.FavouriteToggled("1"))

        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.FAVOURITE)
    }

    @Test
    fun `toggling a favourite twice returns to none`() = runTest {
        val model = viewModel()

        model.dispatch(PropertyListIntent.FavouriteToggled("1"))
        model.dispatch(PropertyListIntent.FavouriteToggled("1"))

        assertThat(propertyRepository.currentFlags()).doesNotContainKey("1")
    }

    @Test
    fun `the view model never guesses the new flag optimistically`() = runTest {
        val model = viewModel()
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        model.dispatch(PropertyListIntent.FavouriteToggled("1"))

        // The fake repository does not push the change back through
        // observeVisibleProperties, so the state must still show the old value.
        // Guessing here would risk the UI disagreeing with the database.
        assertThat(model.state.value.properties.single().isFavourite).isFalse()
    }

    @Test
    fun `discarding writes the discarded flag`() = runTest {
        val model = viewModel()

        model.dispatch(PropertyListIntent.DiscardClicked("1"))

        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.DISCARDED)
    }

    @Test
    fun `a failed write raises an error effect and changes nothing`() = runTest {
        val model = viewModel()
        propertyRepository.failWith = UciError.LocalStorage("disk full")

        model.effects.test {
            model.dispatch(PropertyListIntent.DiscardClicked("1"))

            assertThat(awaitItem())
                .isEqualTo(PropertyListEffect.ShowError(UciError.LocalStorage("disk full")))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(propertyRepository.currentFlags()).isEmpty()
    }

    // ----------------------------------------------------------- Undo banner

    @Test
    fun `the banner is hidden with nothing discarded`() = runTest {
        val model = viewModel()

        propertyRepository.emitDiscarded(emptyList())

        assertThat(model.state.value.showsUndoBanner).isFalse()
    }

    @Test
    fun `one discarded item exposes it as the undo target`() = runTest {
        val model = viewModel()

        propertyRepository.emitDiscarded(listOf(AppFixtures.discarded("7")))

        assertThat(model.state.value.showsUndoBanner).isTrue()
        assertThat(model.state.value.discardedCount).isEqualTo(1)
        assertThat(model.state.value.lastDiscardedId).isEqualTo("7")
    }

    @Test
    fun `the undo target is the most recently discarded one`() = runTest {
        val model = viewModel()

        // The projection is ordered newest first.
        propertyRepository.emitDiscarded(
            listOf(
                AppFixtures.discarded("3", at = 300),
                AppFixtures.discarded("1", at = 100),
            ),
        )

        assertThat(model.state.value.lastDiscardedId).isEqualTo("3")
    }

    @Test
    fun `undo restores the last discarded property`() = runTest {
        val model = viewModel()
        propertyRepository.emitDiscarded(listOf(AppFixtures.discarded("3")))

        model.dispatch(PropertyListIntent.UndoLastDiscardClicked)

        assertThat(propertyRepository.writes).containsExactly("3" to PropertyFlag.NONE)
    }

    @Test
    fun `undo with nothing discarded is a no-op, not a crash`() = runTest {
        val model = viewModel()

        model.dispatch(PropertyListIntent.UndoLastDiscardClicked)

        assertThat(propertyRepository.writes).isEmpty()
    }

    @Test
    fun `several discarded items open the sheet instead of restoring blindly`() = runTest {
        val model = viewModel()
        propertyRepository.emitDiscarded(
            listOf(AppFixtures.discarded("1"), AppFixtures.discarded("2")),
        )

        model.effects.test {
            model.dispatch(PropertyListIntent.ShowDiscardedClicked)

            assertThat(awaitItem()).isEqualTo(PropertyListEffect.OpenDiscardedSheet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------ Navigation

    @Test
    fun `a card tap carries the visible photo index for the shared transition`() = runTest {
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyListIntent.PropertyClicked("1", imageIndex = 4))

            val effect = awaitItem() as PropertyListEffect.OpenDetail
            assertThat(effect.imageIndex).isEqualTo(4)
            assertThat(effect.focusOnMap).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
