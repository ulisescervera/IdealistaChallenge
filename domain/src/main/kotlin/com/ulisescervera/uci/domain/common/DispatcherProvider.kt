package com.ulisescervera.uci.domain.common

import kotlin.coroutines.CoroutineContext

/**
 * Indirection over the coroutine dispatchers so that use cases and repositories
 * never reference `Dispatchers.IO` directly. Tests swap in an unconfined /
 * test dispatcher; production wiring lives in `:app`.
 */
interface DispatcherProvider {
    val main: CoroutineContext
    val default: CoroutineContext
    val io: CoroutineContext
}
