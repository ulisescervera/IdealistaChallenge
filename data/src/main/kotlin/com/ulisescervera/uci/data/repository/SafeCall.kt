package com.ulisescervera.uci.data.repository

import com.ulisescervera.uci.data.network.ErrorMapper
import com.ulisescervera.uci.domain.common.UciResult
import kotlinx.coroutines.CancellationException

/**
 * The only `try/catch` in `:data`.
 *
 * Every repository call funnels through here so that (a) no `Throwable` ever
 * escapes the layer, and (b) [CancellationException] is re-thrown instead of
 * being swallowed -- a `catch (e: Exception)` that eats cancellation is how a
 * ViewModel ends up leaking a job past `onCleared`.
 */
internal inline fun <T> ErrorMapper.runCatchingUci(block: () -> T): UciResult<T> = try {
    UciResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    UciResult.Failure(map(throwable))
}
