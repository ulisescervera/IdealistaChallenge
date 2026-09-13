package com.ulisescervera.uci.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ulisescervera.uci.core.mvi.UciViewModel
import com.ulisescervera.uci.core.mvi.UiEffect
import com.ulisescervera.uci.domain.usecase.ObservePropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The viewer navigates nowhere and shows no messages, so it raises no effects. */
sealed interface ImageViewerEffect : UiEffect

/**
 * The full-screen image viewer.
 *
 * Images come from the cache, preferring the detail's list over the summary's:
 * `detail.json` returns 10 photos where `list.json` returned 7, and the viewer
 * should show everything known about the property.
 *
 * The viewer always opens on the vertical list, which is the order the brief
 * specifies. The index the user tapped in the detail carousel is kept as a
 * scroll position, not as a mode: opening straight into the pager would make the
 * vertical list unreachable except by pressing back.
 *
 * Mode and immersive state live here, not in composition, because this is the
 * one screen in UCI that is *required* to rotate. `remember` would reset on
 * every orientation change; a ViewModel survives it, and `SavedStateHandle`
 * carries the arguments through process death.
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeProperty: ObservePropertyUseCase,
    private val observePropertyDetail: ObservePropertyDetailUseCase,
) : UciViewModel<ImageViewerUiState, ImageViewerIntent, ImageViewerEffect>(
    ImageViewerUiState(
        propertyId = savedStateHandle.get<String>(ARG_PROPERTY_ID).orEmpty(),
        mode = ImageViewerUiState.Mode.VerticalList,
        initialImageIndex = (savedStateHandle.get<Int>(ARG_INITIAL_IMAGE_INDEX) ?: 0).coerceAtLeast(0),
    ),
) {

    init {
        val id = currentState.propertyId
        if (id.isNotBlank()) {
            viewModelScope.launch {
                combine(
                    observeProperty(id),
                    observePropertyDetail(id),
                ) { summary, detail ->
                    // Detail first: it is the longer list.
                    detail?.carouselImages?.takeIf { it.isNotEmpty() }
                        ?: summary?.carouselImages
                        ?: emptyList()
                }.collect { images ->
                    reduce { copy(images = images, isLoading = false) }
                }
            }
        } else {
            reduce { copy(isLoading = false) }
        }
    }

    override fun onIntent(intent: ImageViewerIntent) {
        when (intent) {
            is ImageViewerIntent.PhotoOpened ->
                reduce { copy(mode = ImageViewerUiState.Mode.Pager(intent.index)) }

            ImageViewerIntent.ImmersiveToggled ->
                reduce { copy(isImmersive = !isImmersive) }

            ImageViewerIntent.PagerDismissed -> reduce {
                // Leaving the pager must also leave immersive mode, or the grid
                // would render behind hidden system bars with no way to get them
                // back.
                copy(mode = ImageViewerUiState.Mode.VerticalList, isImmersive = false)
            }
        }
    }

    companion object {
        const val ARG_PROPERTY_ID = "propertyId"
        const val ARG_INITIAL_IMAGE_INDEX = "initialImageIndex"
    }
}
