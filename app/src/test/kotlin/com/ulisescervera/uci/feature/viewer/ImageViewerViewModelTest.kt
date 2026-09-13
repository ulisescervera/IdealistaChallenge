package com.ulisescervera.uci.feature.viewer

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.usecase.ObservePropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyUseCase
import com.ulisescervera.uci.support.AppFixtures
import com.ulisescervera.uci.support.FakePropertyRepository
import com.ulisescervera.uci.support.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ImageViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val propertyRepository = FakePropertyRepository()

    private fun viewModel(propertyId: String = "1", initialIndex: Int = 0) = ImageViewerViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                ImageViewerViewModel.ARG_PROPERTY_ID to propertyId,
                // SafeArgs always writes this key, because the argument has a
                // default -- which is exactly why the viewer cannot use its
                // absence to decide the mode.
                ImageViewerViewModel.ARG_INITIAL_IMAGE_INDEX to initialIndex,
            ),
        ),
        observeProperty = ObservePropertyUseCase(propertyRepository),
        observePropertyDetail = ObservePropertyDetailUseCase(propertyRepository),
    )

    @Test
    fun `the viewer always opens on the vertical list, as the brief specifies`() = runTest {
        val model = viewModel(initialIndex = 3)

        // Opening straight into the pager would make the vertical list
        // unreachable except by pressing back.
        assertThat(model.state.value.mode).isEqualTo(ImageViewerUiState.Mode.VerticalList)
    }

    @Test
    fun `the tapped photo becomes the initial scroll position of the list`() = runTest {
        val model = viewModel(initialIndex = 3)

        assertThat(model.state.value.initialImageIndex).isEqualTo(3)
    }

    @Test
    fun `a negative index is clamped rather than trusted`() = runTest {
        val model = viewModel(initialIndex = -7)

        assertThat(model.state.value.initialImageIndex).isEqualTo(0)
    }

    @Test
    fun `the detail image list wins over the summary because it is longer`() = runTest {
        // list.json returns 7 photos for property 1; detail.json returns 10.
        val summary = AppFixtures.property("1", images = listOf(AppFixtures.image("a")))
        val detail = AppFixtures.detail(summary).copy(
            images = listOf(AppFixtures.image("a"), AppFixtures.image("b"), AppFixtures.image("c")),
        )
        propertyRepository.emitVisible(listOf(summary))
        propertyRepository.emitDetail(detail)

        val model = viewModel()

        assertThat(model.state.value.images).hasSize(3)
        assertThat(model.state.value.isLoading).isFalse()
    }

    @Test
    fun `the summary images are used while the detail has not arrived`() = runTest {
        propertyRepository.emitVisible(listOf(AppFixtures.property("1")))

        val model = viewModel()

        assertThat(model.state.value.images).hasSize(3)
    }

    @Test
    fun `tapping a photo in the vertical list opens the pager`() = runTest {
        val model = viewModel()
        assertThat(model.state.value.mode).isEqualTo(ImageViewerUiState.Mode.VerticalList)

        model.dispatch(ImageViewerIntent.PhotoOpened(2))

        assertThat(model.state.value.mode).isEqualTo(ImageViewerUiState.Mode.Pager(2))
    }

    @Test
    fun `immersive mode hides the system bars only inside the pager`() = runTest {
        val model = viewModel()

        // Immersive over the vertical list would leave the user with no way back.
        model.dispatch(ImageViewerIntent.ImmersiveToggled)
        assertThat(model.state.value.showsSystemBars).isTrue()

        model.dispatch(ImageViewerIntent.PhotoOpened(0))
        assertThat(model.state.value.showsSystemBars).isFalse()
    }

    @Test
    fun `toggling immersive twice brings the bars back`() = runTest {
        val model = viewModel()
        model.dispatch(ImageViewerIntent.PhotoOpened(0))

        model.dispatch(ImageViewerIntent.ImmersiveToggled)
        assertThat(model.state.value.showsSystemBars).isFalse()

        model.dispatch(ImageViewerIntent.ImmersiveToggled)
        assertThat(model.state.value.showsSystemBars).isTrue()
    }

    @Test
    fun `leaving the pager also leaves immersive mode`() = runTest {
        val model = viewModel()
        model.dispatch(ImageViewerIntent.PhotoOpened(0))
        model.dispatch(ImageViewerIntent.ImmersiveToggled)

        model.dispatch(ImageViewerIntent.PagerDismissed)

        assertThat(model.state.value.mode).isEqualTo(ImageViewerUiState.Mode.VerticalList)
        assertThat(model.state.value.isImmersive).isFalse()
        assertThat(model.state.value.showsSystemBars).isTrue()
    }

    @Test
    fun `a blank id stops loading instead of hanging on a skeleton`() = runTest {
        val model = viewModel(propertyId = "")

        assertThat(model.state.value.isLoading).isFalse()
        assertThat(model.state.value.images).isEmpty()
    }
}
