package com.ulisescervera.uci.feature.list

import com.ulisescervera.uci.core.mvi.UiEffect
import com.ulisescervera.uci.core.mvi.UiIntent
import com.ulisescervera.uci.core.mvi.UiState
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.feature.list.filter.PropertyFilters

/**
 * The full contract of the property list screen: what the user can do
 * ([PropertyListIntent]), what the screen looks like ([PropertyListUiState]) and
 * what happens outside it ([PropertyListEffect]).
 *
 * Living in `:app` and not in `:domain` is a deliberate decision: intents name a
 * gesture on a specific screen and carry presentation types, not domain ones.
 */

sealed interface PropertyListIntent : UiIntent {

    /** Sent from `onViewCreated`; triggers the first refresh if the cache is cold. */
    data object ScreenStarted : PropertyListIntent

    data object RefreshRequested : PropertyListIntent

    data object RetryRequested : PropertyListIntent

    /**
     * The card body was tapped.
     *
     * [imageIndex] travels with the intent so the detail can open on the same
     * photo the user was looking at and the shared-element transition has a
     * matching pair. This is exactly the kind of presentation-shaped payload
     * that keeps intents out of `:domain`.
     */
    data class PropertyClicked(val propertyId: String, val imageIndex: Int) : PropertyListIntent

    data class FavouriteToggled(val propertyId: String) : PropertyListIntent

    data class DiscardClicked(val propertyId: String) : PropertyListIntent

    /** Banner action when exactly one property is discarded. */
    data object UndoLastDiscardClicked : PropertyListIntent

    /** Banner action when several are discarded: open the sheet. */
    data object ShowDiscardedClicked : PropertyListIntent

    /** The toolbar's filter icon: open the sheet with the current filters. */
    data object FilterButtonClicked : PropertyListIntent

    /** The toolbar's map icon: open the "all properties" map. */
    data object MapButtonClicked : PropertyListIntent

    data class FiltersApplied(val filters: PropertyFilters) : PropertyListIntent
}

/**
 * One immutable object describing the whole screen.
 *
 * [surface] is derived rather than stored, which is what makes "loading and
 * error at the same time" unrepresentable: the fragment renders exactly one
 * branch and the precedence rules live here, next to the data, instead of being
 * re-implemented in the view.
 */
data class PropertyListUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val properties: List<Property> = emptyList(),
    val discarded: List<DiscardedProperty> = emptyList(),
    val error: UciError? = null,
    val filters: PropertyFilters = PropertyFilters(),
) : UiState {

    val discardedCount: Int get() = discarded.size

    /** Target of the single-item undo; the projection is ordered newest first. */
    val lastDiscardedId: String? get() = discarded.firstOrNull()?.id

    val showsUndoBanner: Boolean get() = discarded.isNotEmpty()

    /** What the list actually renders: [properties] with [filters] applied. */
    val visibleProperties: List<Property> get() = properties.filter(filters::matches)

    /**
     * Note the ordering: cached content wins over an error. Failing to refresh
     * while four properties are already on screen is a snackbar, not an error
     * screen -- that is the whole payoff of the offline-first repository.
     *
     * [FilteredEmpty] is deliberately its own branch and not folded into
     * [Empty]: "the feed has nothing" and "the feed has plenty, but your
     * filters exclude all of it" call for different copy and a different
     * recovery action (clear filters vs. nothing to do).
     */
    val surface: Surface
        get() = when {
            isInitialLoading -> Surface.Skeleton
            properties.isNotEmpty() && visibleProperties.isNotEmpty() -> Surface.Content
            properties.isNotEmpty() -> Surface.FilteredEmpty
            error != null -> Surface.Error
            discarded.isNotEmpty() -> Surface.AllDiscarded
            else -> Surface.Empty
        }

    enum class Surface {
        /** First load, nothing cached: shimmering placeholder rows. */
        Skeleton,
        Content,

        /** The feed is genuinely empty. */
        Empty,

        /** There is content, but the active filters exclude all of it. */
        FilteredEmpty,

        /** Everything the feed has was discarded by the user -- different copy. */
        AllDiscarded,

        /** Nothing cached and the refresh failed. */
        Error,
    }
}

sealed interface PropertyListEffect : UiEffect {

    data class OpenDetail(
        val propertyId: String,
        val imageIndex: Int,
        val focusOnMap: Boolean,
    ) : PropertyListEffect

    data object OpenDiscardedSheet : PropertyListEffect

    data class OpenFiltersSheet(val filters: PropertyFilters) : PropertyListEffect

    data object OpenMap : PropertyListEffect

    /** A refresh failed but there is cached content: show a transient message. */
    data class ShowError(val error: UciError) : PropertyListEffect
}
