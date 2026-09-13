package com.ulisescervera.uci.feature.viewer

import com.ulisescervera.uci.core.mvi.UiIntent
import com.ulisescervera.uci.core.mvi.UiState
import com.ulisescervera.uci.domain.model.PropertyImage

sealed interface ImageViewerIntent : UiIntent {

    /** From the vertical list: open the horizontal pager on this photo. */
    data class PhotoOpened(val index: Int) : ImageViewerIntent

    /** In pager mode: toggle the immersive, full-black presentation. */
    data object ImmersiveToggled : ImageViewerIntent

    /** Back from the pager to the vertical list. */
    data object PagerDismissed : ImageViewerIntent
}

/**
 * State of the image viewer.
 *
 * Two nested modes, in the order the brief describes:
 *
 * ```
 * VerticalList  <- always the entry point: every photo, scrolled vertically
 *   tap ->  Pager(index)      horizontal carousel starting on that photo
 *             tap ->  isImmersive = true    system bars hidden, black backdrop
 * ```
 *
 * [initialImageIndex] is the photo that was tapped in the detail carousel. It is
 * *not* used to open the pager -- the brief puts the vertical list first -- but
 * it does scroll the list to that photo, so the user does not lose their place.
 *
 * [mode] and [isImmersive] are kept in the ViewModel rather than in
 * `remember`ed composition state precisely because this screen is required to
 * rotate: a rotation destroys the composition, and the user must not be thrown
 * back to the list halfway through browsing.
 */
data class ImageViewerUiState(
    val propertyId: String = "",
    val images: List<PropertyImage> = emptyList(),
    val mode: Mode = Mode.VerticalList,
    val isImmersive: Boolean = false,
    val isLoading: Boolean = true,
    /** Where to scroll the vertical list on first composition. */
    val initialImageIndex: Int = 0,
) : UiState {

    val isPagerMode: Boolean get() = mode is Mode.Pager

    /** Immersive only makes sense over a single photo, never over the grid. */
    val showsSystemBars: Boolean get() = !(isPagerMode && isImmersive)

    sealed interface Mode {
        data object VerticalList : Mode
        data class Pager(val initialIndex: Int) : Mode
    }
}
