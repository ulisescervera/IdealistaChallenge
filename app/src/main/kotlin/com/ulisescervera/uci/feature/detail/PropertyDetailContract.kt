package com.ulisescervera.uci.feature.detail

import com.ulisescervera.uci.core.mvi.UiEffect
import com.ulisescervera.uci.core.mvi.UiIntent
import com.ulisescervera.uci.core.mvi.UiState
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyDetail

sealed interface PropertyDetailIntent : UiIntent {

    data object ScreenStarted : PropertyDetailIntent

    data object RetryRequested : PropertyDetailIntent

    data object FavouriteToggled : PropertyDetailIntent

    data object DiscardToggled : PropertyDetailIntent

    data object ShareClicked : PropertyDetailIntent

    /** "Leer más" / "Leer menos" on the advertiser comment. */
    data object CommentExpansionToggled : PropertyDetailIntent

    data class ImageClicked(val imageIndex: Int) : PropertyDetailIntent

    /** The map page of the carousel: scroll down to the interactive map. */
    data object MapPageClicked : PropertyDetailIntent

    /** The map row itself: open it at full screen. */
    data class MapClicked(val location: GeoPoint, val addressLine: String?) : PropertyDetailIntent

    /**
     * The user scrolled to the bottom. Triggers the related-properties load,
     * once.
     */
    data object ReachedEnd : PropertyDetailIntent

    data class RelatedPropertyClicked(val propertyId: String) : PropertyDetailIntent

    /**
     * The fragment has performed the pending scroll-to-map, so the flag can be
     * cleared and a rotation will not scroll again.
     */
    data object MapFocusConsumed : PropertyDetailIntent
}

/**
 * State of the detail screen.
 *
 * The two-source design is the heart of it: [summary] comes from the local cache
 * and is available immediately, [detail] arrives from the network. That is what
 * lets the screen render the carousel, the name, the price, the size, the rooms,
 * the floor, the lift and the garage straight away -- exactly the set the brief
 * asks for -- and fill in the comment, the characteristics and the energy
 * certificate afterwards.
 *
 * The garage is only ever in [summary]: `detail.json` does not report it.
 */
data class PropertyDetailUiState(
    val propertyId: String = "",
    val summary: Property? = null,
    val detail: PropertyDetail? = null,
    val isLoadingDetail: Boolean = true,
    val related: RelatedState = RelatedState.Idle,
    val isCommentExpanded: Boolean = false,
    val error: UciError? = null,
    /** True until the fragment has scrolled to the map row after a map deep link. */
    val isMapFocusPending: Boolean = false,
    /** Carousel page to open on, so the shared element lands on the right photo. */
    val initialImageIndex: Int = 0,
) : UiState {

    /** Anything at all to render, even if the network has not answered. */
    val hasContent: Boolean get() = summary != null || detail != null

    /**
     * A full-screen error is only correct when there is nothing else to show.
     * With a cached summary on screen, a failed detail fetch is a snackbar.
     */
    val showsBlockingError: Boolean get() = !hasContent && error != null

    val hasLocation: Boolean get() = detail?.hasLocation ?: (summary?.hasLocation == true)
}

/** Lifecycle of the "more like this" block. */
sealed interface RelatedState {

    /** Not requested yet: the user has not reached the bottom. */
    data object Idle : RelatedState

    data object Loading : RelatedState

    data class Loaded(val items: List<Property>) : RelatedState

    data class Failed(val error: UciError) : RelatedState
}

sealed interface PropertyDetailEffect : UiEffect {

    data class OpenImageViewer(val propertyId: String, val imageIndex: Int) : PropertyDetailEffect

    data object ScrollToMap : PropertyDetailEffect

    data class OpenFullMap(val location: GeoPoint, val addressLine: String?) : PropertyDetailEffect

    data class ShareProperty(val property: Property) : PropertyDetailEffect

    data class OpenRelatedProperty(val propertyId: String) : PropertyDetailEffect

    data class ShowError(val error: UciError) : PropertyDetailEffect

    /** Discarding from the detail closes it: the property is no longer listed. */
    data object CloseAfterDiscard : PropertyDetailEffect
}
