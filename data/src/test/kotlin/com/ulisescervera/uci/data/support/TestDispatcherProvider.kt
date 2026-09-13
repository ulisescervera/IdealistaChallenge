package com.ulisescervera.uci.data.support

import com.ulisescervera.uci.domain.common.DispatcherProvider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Every dispatcher is the same test dispatcher, so `withContext(io)` inside a
 * repository does not hop threads and `runTest` stays in control of virtual
 * time.
 *
 * This is the single most valuable consequence of injecting [DispatcherProvider]
 * instead of touching `Dispatchers.IO` directly: without it, every repository
 * test would need `Dispatchers.setMain` plus luck.
 */
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineContext get() = dispatcher
    override val default: CoroutineContext get() = dispatcher
    override val io: CoroutineContext get() = dispatcher
}
