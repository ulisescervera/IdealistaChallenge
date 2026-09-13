package com.ulisescervera.uci.feature.discarded

import com.ulisescervera.uci.core.mvi.UiEffect
import com.ulisescervera.uci.core.mvi.UiIntent
import com.ulisescervera.uci.core.mvi.UiState
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.model.DiscardedProperty

sealed interface DiscardedPropertiesIntent : UiIntent {

    data class RestoreClicked(val propertyId: String) : DiscardedPropertiesIntent

    data object RestoreAllClicked : DiscardedPropertiesIntent
}

data class DiscardedPropertiesUiState(
    val items: List<DiscardedProperty> = emptyList(),
    /**
     * True only after the first emission from Room. Before that, "empty" and
     * "not loaded yet" look identical and the sheet would flash its empty copy.
     */
    val hasLoaded: Boolean = false,
) : UiState {
    val isEmpty: Boolean get() = hasLoaded && items.isEmpty()
}

sealed interface DiscardedPropertiesEffect : UiEffect {

    /** @param count how many came back, for the confirmation copy. */
    data class Restored(val count: Int) : DiscardedPropertiesEffect

    /**
     * Nothing left to show. The sheet closes itself rather than sitting there
     * empty: it was opened from a banner that no longer exists.
     */
    data object Dismiss : DiscardedPropertiesEffect

    data class ShowError(val error: UciError) : DiscardedPropertiesEffect
}
