package com.ulisescervera.uci.core.ui.ext

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects [this] only while [owner] is at least STARTED, and *cancels* the
 * collection when it stops.
 *
 * The distinction from `launchWhenStarted` matters: that one merely suspends the
 * collector, so a `Flow` that emits while the screen is backgrounded still
 * buffers work and delivers a burst on resume. `repeatOnLifecycle` tears the
 * collection down instead, which for a Room-backed flow means the query is
 * unsubscribed while the user is elsewhere.
 *
 * Always pass `viewLifecycleOwner` from a fragment, never the fragment itself.
 */
inline fun <T> Flow<T>.collectWhileStarted(
    owner: LifecycleOwner,
    crossinline onEach: suspend (T) -> Unit,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { onEach(it) }
        }
    }
}
