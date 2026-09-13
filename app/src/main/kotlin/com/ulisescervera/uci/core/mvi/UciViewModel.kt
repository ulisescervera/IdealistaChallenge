package com.ulisescervera.uci.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel: one state stream, one effect stream, one entry point.
 *
 * Design notes worth the paragraph:
 *
 * - **`dispatch` is the only public mutator.** The fragment cannot reach into
 *   the state, which is what makes the ViewModel testable by feeding it intents
 *   and asserting states, with no view involved.
 * - **Effects use a `MutableSharedFlow` with `extraBufferCapacity = 1` and
 *   `DROP_OLDEST`**, not a `Channel`. A channel would suspend the emitter while
 *   the fragment is in the background, and it silently drops nothing -- so a
 *   navigation effect queued during a rotation would fire twice. Dropping the
 *   stale effect is the correct behaviour for "navigate" and "show snackbar".
 * - **`onIntent` is abstract and every implementation is an exhaustive `when`**,
 *   so adding an intent without handling it does not compile.
 */
abstract class UciViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: Flow<E> = mutableEffects.asSharedFlow()

    protected val currentState: S get() = mutableState.value

    /** The single door into the ViewModel. Called from the view layer only. */
    fun dispatch(intent: I) = onIntent(intent)

    protected abstract fun onIntent(intent: I)

    /**
     * Atomic state update. [reducer] must be pure: it can be re-invoked when
     * two dispatches race, exactly like `MutableStateFlow.update`.
     */
    protected fun reduce(reducer: S.() -> S) = mutableState.update(reducer)

    protected fun emitEffect(effect: E) {
        // tryEmit never suspends and cannot fail with this buffer configuration,
        // so effects can be raised from non-suspending reducers.
        if (!mutableEffects.tryEmit(effect)) {
            viewModelScope.launch { mutableEffects.emit(effect) }
        }
    }
}
