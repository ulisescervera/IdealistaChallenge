package com.ulisescervera.uci.feature.discarded

import androidx.lifecycle.viewModelScope
import com.ulisescervera.uci.core.mvi.UciViewModel
import com.ulisescervera.uci.domain.common.fold
import com.ulisescervera.uci.domain.common.onFailure
import com.ulisescervera.uci.domain.usecase.ObserveDiscardedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RestoreAllDiscardedUseCase
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The "borrados" sheet.
 *
 * Restoring one item is a write to `property_flags`; the Room flow then re-emits
 * a shorter list, which is what makes the row disappear. The ViewModel never
 * removes an item from its own state -- there is one source of truth and it is
 * the database, so the sheet and the list underneath can never disagree.
 *
 * When the list empties out, the sheet asks to be dismissed instead of showing
 * "no hay inmuebles borrados": it was reached from a banner that has, by then,
 * also disappeared.
 */
@HiltViewModel
class DiscardedPropertiesViewModel @Inject constructor(
    private val observeDiscardedProperties: ObserveDiscardedPropertiesUseCase,
    private val restoreProperty: RestorePropertyUseCase,
    private val restoreAllDiscarded: RestoreAllDiscardedUseCase,
) : UciViewModel<DiscardedPropertiesUiState, DiscardedPropertiesIntent, DiscardedPropertiesEffect>(
    DiscardedPropertiesUiState(),
) {

    init {
        viewModelScope.launch {
            observeDiscardedProperties().collect { items ->
                val hadItems = currentState.items.isNotEmpty()
                reduce { copy(items = items, hasLoaded = true) }
                // Only dismiss on a *transition* to empty. Emitting Dismiss on
                // the very first (still empty) emission would close the sheet
                // before the user saw it.
                if (hadItems && items.isEmpty()) emitEffect(DiscardedPropertiesEffect.Dismiss)
            }
        }
    }

    override fun onIntent(intent: DiscardedPropertiesIntent) = when (intent) {
        is DiscardedPropertiesIntent.RestoreClicked -> restoreOne(intent.propertyId)
        DiscardedPropertiesIntent.RestoreAllClicked -> restoreAll()
    }

    private fun restoreOne(propertyId: String) {
        viewModelScope.launch {
            restoreProperty(propertyId).fold(
                onSuccess = { emitEffect(DiscardedPropertiesEffect.Restored(count = 1)) },
                onFailure = { emitEffect(DiscardedPropertiesEffect.ShowError(it)) },
            )
        }
    }

    private fun restoreAll() {
        viewModelScope.launch {
            restoreAllDiscarded()
                .fold(
                    onSuccess = { count -> emitEffect(DiscardedPropertiesEffect.Restored(count)) },
                    onFailure = { error -> emitEffect(DiscardedPropertiesEffect.ShowError(error)) },
                )
        }
    }
}
