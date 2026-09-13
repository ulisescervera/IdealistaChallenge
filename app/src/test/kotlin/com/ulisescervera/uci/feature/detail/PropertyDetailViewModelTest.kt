package com.ulisescervera.uci.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.GetRelatedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import com.ulisescervera.uci.support.AppFixtures
import com.ulisescervera.uci.support.FakePropertyRepository
import com.ulisescervera.uci.support.FakeRelatedRepository
import com.ulisescervera.uci.support.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PropertyDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val propertyRepository = FakePropertyRepository()
    private val relatedRepository = FakeRelatedRepository()

    private fun viewModel(
        propertyId: String = "1",
        focusOnMap: Boolean = false,
        initialImageIndex: Int = 0,
    ) = PropertyDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                PropertyDetailViewModel.ARG_PROPERTY_ID to propertyId,
                PropertyDetailViewModel.ARG_FOCUS_ON_MAP to focusOnMap,
                PropertyDetailViewModel.ARG_INITIAL_IMAGE_INDEX to initialImageIndex,
            ),
        ),
        observeProperty = ObservePropertyUseCase(propertyRepository),
        observePropertyDetail = ObservePropertyDetailUseCase(propertyRepository),
        refreshPropertyDetail = RefreshPropertyDetailUseCase(propertyRepository),
        refreshProperties = RefreshPropertiesUseCase(propertyRepository),
        toggleFavourite = ToggleFavouriteUseCase(propertyRepository),
        discardProperty = DiscardPropertyUseCase(propertyRepository),
        restoreProperty = RestorePropertyUseCase(propertyRepository),
        getRelatedProperties = GetRelatedPropertiesUseCase(relatedRepository),
    )

    // ----------------------------------------------------- Progressive load

    @Test
    fun `arguments are read from the saved state handle`() = runTest {
        val model = viewModel(propertyId = "7", focusOnMap = true, initialImageIndex = 3)

        assertThat(model.state.value.propertyId).isEqualTo("7")
        assertThat(model.state.value.isMapFocusPending).isTrue()
        assertThat(model.state.value.initialImageIndex).isEqualTo(3)
    }

    @Test
    fun `the cached summary renders before the network answers`() = runTest {
        val model = viewModel()

        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        // This is what makes the header appear instantly and the comment show a
        // skeleton -- exactly the loading behaviour the brief asks for.
        assertThat(model.state.value.summary).isNotNull()
        assertThat(model.state.value.detail).isNull()
        assertThat(model.state.value.hasContent).isTrue()
        assertThat(model.state.value.showsBlockingError).isFalse()
    }

    @Test
    fun `the detail fills in once fetched`() = runTest {
        val summary = AppFixtures.property("1")
        propertyRepository.emitVisible(listOf(summary))
        propertyRepository.emitDetail(AppFixtures.detail(summary))
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ScreenStarted)

        assertThat(model.state.value.detail).isNotNull()
        assertThat(model.state.value.isLoadingDetail).isFalse()
    }

    @Test
    fun `the detail is fetched once per view model`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        propertyRepository.emitDetail(AppFixtures.detail())
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ScreenStarted)
        model.dispatch(PropertyDetailIntent.ScreenStarted)

        assertThat(propertyRepository.refreshDetailCallCount).isEqualTo(1)
    }

    @Test
    fun `a deep link with a cold cache refreshes the feed first`() = runTest {
        // Nothing cached: observeProperty would emit null forever without this.
        propertyRepository.onRefreshEmit = listOf(AppFixtures.property("1"))
        propertyRepository.emitDetail(AppFixtures.detail())
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ScreenStarted)

        assertThat(propertyRepository.refreshListCallCount).isEqualTo(1)
        assertThat(model.state.value.summary).isNotNull()
    }

    @Test
    fun `a tap does not refresh the feed because the property is already cached`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        propertyRepository.emitDetail(AppFixtures.detail())
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ScreenStarted)

        assertThat(propertyRepository.refreshListCallCount).isEqualTo(0)
    }

    @Test
    fun `a blank id fails immediately instead of loading forever`() = runTest {
        val model = viewModel(propertyId = "")

        assertThat(model.state.value.isLoadingDetail).isFalse()
        assertThat(model.state.value.error).isInstanceOf(UciError.PropertyNotFound::class.java)
        assertThat(model.state.value.showsBlockingError).isTrue()
    }

    // ------------------------------------------------------------- Errors

    @Test
    fun `a detail failure with a cached summary is a snackbar, not a takeover`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        propertyRepository.refreshDetailResult = { UciResult.Failure(UciError.Timeout) }
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyDetailIntent.ScreenStarted)

            assertThat(awaitItem()).isEqualTo(PropertyDetailEffect.ShowError(UciError.Timeout))
            // The header is already useful, so it stays.
            assertThat(model.state.value.showsBlockingError).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a detail failure with nothing cached is a blocking error`() = runTest {
        propertyRepository.refreshDetailResult = { UciResult.Failure(UciError.NoConnectivity) }
        propertyRepository.refreshListResult = UciResult.Failure(UciError.NoConnectivity)
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ScreenStarted)

        assertThat(model.state.value.showsBlockingError).isTrue()
    }

    // -------------------------------------------------------------- Actions

    @Test
    fun `favouriting writes the flag`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.FavouriteToggled)

        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.FAVOURITE)
    }

    @Test
    fun `discarding closes the screen`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyDetailIntent.DiscardToggled)

            // Staying on the detail of something you just removed is a dead end.
            assertThat(awaitItem()).isEqualTo(PropertyDetailEffect.CloseAfterDiscard)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.DISCARDED)
    }

    @Test
    fun `discard is a toggle here, unlike the list's one way remove`() = runTest {
        propertyRepository.emitVisible(
            listOf(AppFixtures.property("1", flag = PropertyFlag.DISCARDED)),
        )
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.DiscardToggled)

        // The button shows the current state, so flipping it back is
        // discoverable -- and it must not close the screen.
        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.NONE)
    }

    @Test
    fun `sharing carries the property, and does nothing without one`() = runTest {
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ShareClicked)
        assertThat(propertyRepository.writes).isEmpty()

        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))
        model.effects.test {
            model.dispatch(PropertyDetailIntent.ShareClicked)

            val effect = awaitItem() as PropertyDetailEffect.ShareProperty
            assertThat(effect.property.id).isEqualTo("1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the comment expansion toggles`() = runTest {
        val model = viewModel()

        assertThat(model.state.value.isCommentExpanded).isFalse()
        model.dispatch(PropertyDetailIntent.CommentExpansionToggled)
        assertThat(model.state.value.isCommentExpanded).isTrue()
        model.dispatch(PropertyDetailIntent.CommentExpansionToggled)
        assertThat(model.state.value.isCommentExpanded).isFalse()
    }

    @Test
    fun `tapping a photo opens the viewer on that photo`() = runTest {
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyDetailIntent.ImageClicked(5))

            assertThat(awaitItem())
                .isEqualTo(PropertyDetailEffect.OpenImageViewer("1", 5))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping the map page scrolls to the map row`() = runTest {
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyDetailIntent.MapPageClicked)

            assertThat(awaitItem()).isEqualTo(PropertyDetailEffect.ScrollToMap)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping the map row opens it at full screen`() = runTest {
        val model = viewModel()
        val location = GeoPoint(40.4, -3.7)

        model.effects.test {
            model.dispatch(PropertyDetailIntent.MapClicked(location, "calle de Lagasca"))

            assertThat(awaitItem()).isEqualTo(PropertyDetailEffect.OpenFullMap(location, "calle de Lagasca"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `map focus is consumed once so a rotation does not scroll again`() = runTest {
        val model = viewModel(focusOnMap = true)

        model.dispatch(PropertyDetailIntent.MapFocusConsumed)

        assertThat(model.state.value.isMapFocusPending).isFalse()
    }

    // ------------------------------------------------------------- Related

    @Test
    fun `related properties are requested once however often the bottom is hit`() = runTest {
        relatedRepository.result = UciResult.Success(listOf(AppFixtures.property("2")))
        val model = viewModel()

        // A scroll listener fires this repeatedly while the user scrubs the end.
        repeat(5) { model.dispatch(PropertyDetailIntent.ReachedEnd) }

        assertThat(relatedRepository.callCount).isEqualTo(1)
    }

    @Test
    fun `related properties land in the loaded state`() = runTest {
        relatedRepository.result = UciResult.Success(
            listOf(AppFixtures.property("2"), AppFixtures.property("3")),
        )
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ReachedEnd)

        val related = model.state.value.related
        assertThat(related).isInstanceOf(RelatedState.Loaded::class.java)
        assertThat((related as RelatedState.Loaded).items.map { it.id })
            .containsExactly("2", "3")
    }

    @Test
    fun `a failed related load is remembered so it is not retried on every scroll`() = runTest {
        relatedRepository.result = UciResult.Failure(UciError.Timeout)
        val model = viewModel()

        model.dispatch(PropertyDetailIntent.ReachedEnd)
        model.dispatch(PropertyDetailIntent.ReachedEnd)

        assertThat(model.state.value.related).isInstanceOf(RelatedState.Failed::class.java)
        assertThat(relatedRepository.callCount).isEqualTo(1)
    }

    @Test
    fun `tapping a related property navigates to its own detail`() = runTest {
        val model = viewModel()

        model.effects.test {
            model.dispatch(PropertyDetailIntent.RelatedPropertyClicked("9"))

            assertThat(awaitItem()).isEqualTo(PropertyDetailEffect.OpenRelatedProperty("9"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
