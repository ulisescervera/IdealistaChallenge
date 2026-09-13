package com.ulisescervera.uci.feature.favourites

import androidx.lifecycle.viewModelScope
import com.ulisescervera.uci.core.mvi.UciViewModel
import com.ulisescervera.uci.domain.common.onFailure
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.ObserveFavouritePropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The favourites tab.
 *
 * There is no refresh here, on purpose: favourites are a *view* of the local
 * database, not of a remote resource. Adding a pull-to-refresh would suggest
 * the server has an opinion about this list, and it does not -- the DTOs carry
 * no favourite flag at all.
 *
 * Un-favouriting from this screen makes the row disappear, because the Room
 * query no longer returns it. The ViewModel does not remove it from its own
 * state: one source of truth, one code path.
 */
@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val observeFavouriteProperties: ObserveFavouritePropertiesUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val discardProperty: DiscardPropertyUseCase,
) : UciViewModel<FavouritesUiState, FavouritesIntent, FavouritesEffect>(FavouritesUiState()) {

    init {
        viewModelScope.launch {
            observeFavouriteProperties().collect { properties ->
                reduce { copy(properties = properties, isLoading = false) }
            }
        }
    }

    override fun onIntent(intent: FavouritesIntent) {
        when (intent) {
            is FavouritesIntent.PropertyClicked ->
                emitEffect(FavouritesEffect.OpenDetail(intent.propertyId))

            is FavouritesIntent.FavouriteToggled -> viewModelScope.launch {
                toggleFavourite(intent.propertyId)
                    .onFailure { emitEffect(FavouritesEffect.ShowError(it)) }
            }

            is FavouritesIntent.DiscardClicked -> viewModelScope.launch {
                // Discarding from here also clears the favourite flag: the two
                // are one exclusive column, so this is a single transaction and
                // the row leaves both lists at once.
                discardProperty(intent.propertyId)
                    .onFailure { emitEffect(FavouritesEffect.ShowError(it)) }
            }
        }
    }
}
