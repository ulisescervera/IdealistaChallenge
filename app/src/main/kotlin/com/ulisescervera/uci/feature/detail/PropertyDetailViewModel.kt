package com.ulisescervera.uci.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ulisescervera.uci.core.mvi.UciViewModel
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.common.fold
import com.ulisescervera.uci.domain.common.onFailure
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.usecase.DiscardPropertyUseCase
import com.ulisescervera.uci.domain.usecase.GetRelatedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.ObservePropertyUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RefreshPropertyDetailUseCase
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import com.ulisescervera.uci.domain.usecase.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The detail screen.
 *
 * ### Progressive rendering
 * Two Room flows are combined: the cached summary (available instantly) and the
 * cached detail (`null` until the network has answered once). The screen shows
 * whatever it has, and the skeleton rows stand in for the sections that are
 * still missing.
 *
 * ### Deep links start colder than taps
 * A tap arrives with the property already cached. A deep link can arrive on a
 * cold start with an empty database, in which case observing the summary would
 * emit `null` forever. [ensureSummary] handles it: if nothing is cached, the
 * feed is refreshed first, and only then does the detail fetch make sense.
 *
 * ### Related properties load once
 * [PropertyDetailIntent.ReachedEnd] fires repeatedly while the user scrubs the
 * bottom of the list. The [RelatedState] guard turns all but the first into a
 * no-op -- without it, a simulated 650 ms request would be issued on every
 * scroll event.
 */
@HiltViewModel
class PropertyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeProperty: ObservePropertyUseCase,
    private val observePropertyDetail: ObservePropertyDetailUseCase,
    private val refreshPropertyDetail: RefreshPropertyDetailUseCase,
    private val refreshProperties: RefreshPropertiesUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val discardProperty: DiscardPropertyUseCase,
    private val restoreProperty: RestorePropertyUseCase,
    private val getRelatedProperties: GetRelatedPropertiesUseCase,
) : UciViewModel<PropertyDetailUiState, PropertyDetailIntent, PropertyDetailEffect>(
    PropertyDetailUiState(
        propertyId = savedStateHandle.get<String>(ARG_PROPERTY_ID).orEmpty(),
        isMapFocusPending = savedStateHandle.get<Boolean>(ARG_FOCUS_ON_MAP) == true,
        initialImageIndex = savedStateHandle.get<Int>(ARG_INITIAL_IMAGE_INDEX) ?: 0,
    ),
) {

    private var hasRequestedDetail = false

    init {
        observeContent()
    }

    private fun observeContent() {
        val id = currentState.propertyId
        if (id.isBlank()) {
            reduce { copy(isLoadingDetail = false, error = UciError.PropertyNotFound(propertyId = "")) }
            return
        }
        viewModelScope.launch {
            combine(
                observeProperty(id),
                observePropertyDetail(id),
            ) { summary, detail -> summary to detail }
                .collect { (summary, detail) ->
                    reduce {
                        copy(
                            summary = summary,
                            detail = detail,
                            // The detail is "loaded" as soon as the cache has
                            // it; a later refresh does not put the skeleton back.
                            isLoadingDetail = detail == null && isLoadingDetail,
                        )
                    }
                }
        }
    }

    override fun onIntent(intent: PropertyDetailIntent) = when (intent) {
        PropertyDetailIntent.ScreenStarted -> loadDetailOnce()
        PropertyDetailIntent.RetryRequested -> retry()
        PropertyDetailIntent.FavouriteToggled -> toggleFavouriteForCurrent()
        PropertyDetailIntent.DiscardToggled -> toggleDiscardForCurrent()
        PropertyDetailIntent.ShareClicked -> share()
        PropertyDetailIntent.CommentExpansionToggled ->
            reduce { copy(isCommentExpanded = !isCommentExpanded) }

        is PropertyDetailIntent.ImageClicked -> emitEffect(
            PropertyDetailEffect.OpenImageViewer(currentState.propertyId, intent.imageIndex),
        )

        PropertyDetailIntent.MapPageClicked -> emitEffect(PropertyDetailEffect.ScrollToMap)
        is PropertyDetailIntent.MapClicked -> emitEffect(
            PropertyDetailEffect.OpenFullMap(intent.location, intent.addressLine),
        )

        PropertyDetailIntent.ReachedEnd -> loadRelatedOnce()
        is PropertyDetailIntent.RelatedPropertyClicked -> emitEffect(
            PropertyDetailEffect.OpenRelatedProperty(intent.propertyId),
        )

        PropertyDetailIntent.MapFocusConsumed -> reduce { copy(isMapFocusPending = false) }
    }

    private fun loadDetailOnce() {
        if (hasRequestedDetail) return
        hasRequestedDetail = true
        viewModelScope.launch {
            ensureSummary()
            fetchDetail()
        }
    }

    /**
     * A deep link can land here with an empty cache. Refreshing the feed first
     * is what turns `uci://property/3` on a cold start into a working screen
     * instead of a permanent skeleton.
     */
    private suspend fun ensureSummary() {
        if (currentState.summary != null) return
        refreshProperties().onFailure { error ->
            reduce { copy(error = error, isLoadingDetail = false) }
        }
    }

    private suspend fun fetchDetail() {
        when (val result = refreshPropertyDetail(currentState.propertyId)) {
            is UciResult.Success -> reduce { copy(isLoadingDetail = false, error = null) }
            is UciResult.Failure -> {
                reduce { copy(isLoadingDetail = false, error = result.error) }
                // With a cached summary on screen this is a snackbar, not a
                // takeover: the header is already useful.
                if (currentState.hasContent) {
                    emitEffect(PropertyDetailEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun retry() {
        reduce { copy(error = null, isLoadingDetail = true) }
        viewModelScope.launch {
            ensureSummary()
            fetchDetail()
        }
    }

    private fun toggleFavouriteForCurrent() {
        viewModelScope.launch {
            toggleFavourite(currentState.propertyId)
                .onFailure { emitEffect(PropertyDetailEffect.ShowError(it)) }
        }
    }

    /**
     * Discarding from the detail is a toggle, unlike the list's one-way remove:
     * here the user can see the current state on the button, so flipping it back
     * is discoverable. Discarding closes the screen -- staying on the detail of
     * something you just removed from the list is a dead end.
     */
    private fun toggleDiscardForCurrent() {
        val isDiscarded = currentState.summary?.flag == PropertyFlag.DISCARDED
        viewModelScope.launch {
            val result = if (isDiscarded) {
                restoreProperty(currentState.propertyId)
            } else {
                discardProperty(currentState.propertyId)
            }
            result.fold(
                onSuccess = {
                    if (!isDiscarded) emitEffect(PropertyDetailEffect.CloseAfterDiscard)
                },
                onFailure = { emitEffect(PropertyDetailEffect.ShowError(it)) },
            )
        }
    }

    private fun share() {
        val property = currentState.summary ?: return
        emitEffect(PropertyDetailEffect.ShareProperty(property))
    }

    private fun loadRelatedOnce() {
        // Idle is the only state from which a request may start. Loading, Loaded
        // and Failed all mean "do not fire again on the next scroll event".
        if (currentState.related != RelatedState.Idle) return
        reduce { copy(related = RelatedState.Loading) }
        viewModelScope.launch {
            getRelatedProperties(currentState.propertyId).fold(
                onSuccess = { items -> reduce { copy(related = RelatedState.Loaded(items)) } },
                onFailure = { error -> reduce { copy(related = RelatedState.Failed(error)) } },
            )
        }
    }

    companion object {
        /** Must match the `<argument>` names in uci_nav_graph.xml. */
        const val ARG_PROPERTY_ID = "propertyId"
        const val ARG_FOCUS_ON_MAP = "focusOnMap"
        const val ARG_INITIAL_IMAGE_INDEX = "initialImageIndex"
    }
}
