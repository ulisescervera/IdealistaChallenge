package com.ulisescervera.uci.feature.list

import androidx.lifecycle.viewModelScope
import com.ulisescervera.uci.core.mvi.UciViewModel
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.common.onFailure
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.ObserveDiscardedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ObserveVisiblePropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The property list.
 *
 * ### Shape
 * One `combine` of two Room-backed flows produces the content; refreshes are
 * fire-and-forget commands whose only visible output is the refresh spinner and,
 * on failure, a snackbar. There is no `properties = result.value` anywhere: the
 * database is the single source of truth, so a favourite toggled here, on the
 * detail screen or in the Compose favourites tab all reach this list through the
 * same query.
 *
 * ### Why the first refresh is conditional
 * [PropertyListIntent.ScreenStarted] fires on every `onViewCreated`, including
 * after a rotation and after returning from the detail. Refreshing every time
 * would hit the network on every navigation. Instead the skeleton is shown only
 * while the cache is cold, and the refresh is issued once per ViewModel
 * instance -- rotation reuses the instance, so it does not re-fetch.
 */
@HiltViewModel
class PropertyListViewModel @Inject constructor(
    private val observeVisibleProperties: ObserveVisiblePropertiesUseCase,
    private val observeDiscardedProperties: ObserveDiscardedPropertiesUseCase,
    private val refreshProperties: RefreshPropertiesUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val discardProperty: DiscardPropertyUseCase,
    private val restoreProperty: RestorePropertyUseCase,
) : UciViewModel<PropertyListUiState, PropertyListIntent, PropertyListEffect>(PropertyListUiState()) {

    private var hasRequestedInitialRefresh = false

    init {
        observeContent()
    }

    /**
     * Both flows come from the same database, so combining them cannot show a
     * torn state: the list and the discarded count are always consistent.
     */
    private fun observeContent() {
        viewModelScope.launch {
            combine(
                observeVisibleProperties(),
                observeDiscardedProperties(),
            ) { properties, discarded -> properties to discarded }
                .collect { (properties, discarded) ->
                    reduce {
                        copy(
                            properties = properties,
                            discarded = discarded,
                            // The first emission from Room ends the skeleton,
                            // even if it is empty: an empty cache with a
                            // finished query is "empty", not "loading".
                            isInitialLoading = false,
                        )
                    }
                }
        }
    }

    override fun onIntent(intent: PropertyListIntent) = when (intent) {
        PropertyListIntent.ScreenStarted -> refreshOnce()
        PropertyListIntent.RefreshRequested -> refresh(showSpinner = true)
        PropertyListIntent.RetryRequested -> retry()
        is PropertyListIntent.PropertyClicked -> openDetail(intent)
        is PropertyListIntent.FavouriteToggled -> toggleFavouriteFor(intent.propertyId)
        is PropertyListIntent.DiscardClicked -> discard(intent.propertyId)
        PropertyListIntent.UndoLastDiscardClicked -> undoLastDiscard()
        PropertyListIntent.ShowDiscardedClicked -> emitEffect(PropertyListEffect.OpenDiscardedSheet)
        PropertyListIntent.FilterButtonClicked ->
            emitEffect(PropertyListEffect.OpenFiltersSheet(currentState.filters))

        PropertyListIntent.MapButtonClicked -> emitEffect(PropertyListEffect.OpenMap)

        is PropertyListIntent.FiltersApplied -> reduce { copy(filters = intent.filters) }
    }

    private fun refreshOnce() {
        if (hasRequestedInitialRefresh) return
        hasRequestedInitialRefresh = true
        refresh(showSpinner = false)
    }

    private fun retry() {
        reduce { copy(error = null, isInitialLoading = properties.isEmpty()) }
        refresh(showSpinner = false)
    }

    /**
     * @param showSpinner true for a pull-to-refresh (the user asked and expects
     *   feedback), false for the automatic first load (the skeleton is already
     *   saying "loading" and two indicators at once look broken).
     */
    private fun refresh(showSpinner: Boolean) {
        viewModelScope.launch {
            if (showSpinner) reduce { copy(isRefreshing = true) }
            val result = refreshProperties()
            reduce {
                copy(
                    isRefreshing = false,
                    // Keep the error in state only while there is nothing to
                    // show; otherwise it is a transient effect.
                    error = (result as? UciResult.Failure)?.error?.takeIf { properties.isEmpty() },
                    isInitialLoading = false,
                )
            }
            result.onFailure { error ->
                if (currentState.properties.isNotEmpty()) {
                    emitEffect(PropertyListEffect.ShowError(error))
                }
            }
        }
    }

    private fun openDetail(intent: PropertyListIntent.PropertyClicked) {
        emitEffect(
            PropertyListEffect.OpenDetail(
                propertyId = intent.propertyId,
                imageIndex = intent.imageIndex,
                focusOnMap = false,
            ),
        )
    }

    private fun toggleFavouriteFor(propertyId: String) {
        viewModelScope.launch {
            // No optimistic update: the write goes to Room and the Room flow
            // pushes the new state back within a frame or two. Guessing here
            // would risk the UI disagreeing with the database.
            toggleFavourite(propertyId).onFailure { emitEffect(PropertyListEffect.ShowError(it)) }
        }
    }

    private fun discard(propertyId: String) {
        viewModelScope.launch {
            discardProperty(propertyId).onFailure { emitEffect(PropertyListEffect.ShowError(it)) }
        }
    }

    private fun undoLastDiscard() {
        val target = currentState.lastDiscardedId ?: return
        viewModelScope.launch {
            restoreProperty(target).onFailure { emitEffect(PropertyListEffect.ShowError(it)) }
        }
    }
}
