package com.ulisescervera.uci.feature.favourites

import com.ulisescervera.uci.core.mvi.UiEffect
import com.ulisescervera.uci.core.mvi.UiIntent
import com.ulisescervera.uci.core.mvi.UiState
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.model.Property

sealed interface FavouritesIntent : UiIntent {

    data class PropertyClicked(val propertyId: String) : FavouritesIntent

    data class FavouriteToggled(val propertyId: String) : FavouritesIntent

    data class DiscardClicked(val propertyId: String) : FavouritesIntent
}

data class FavouritesUiState(
    val properties: List<Property> = emptyList(),
    val isLoading: Boolean = true,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && properties.isEmpty()
}

sealed interface FavouritesEffect : UiEffect {

    data class OpenDetail(val propertyId: String) : FavouritesEffect

    data class ShowError(val error: UciError) : FavouritesEffect
}
