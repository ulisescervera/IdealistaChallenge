package com.ulisescervera.uci.data.network

import com.ulisescervera.uci.domain.common.UciError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * The single place where a [Throwable] becomes a [UciError].
 *
 * Keeping it in one class means the presentation layer never sees an
 * `HttpException` and never has to guess at `cause` chains. It also means a
 * `CancellationException` is re-thrown instead of being reported as an error:
 * a cancelled coroutine is not a failure the user should read about.
 */
@Singleton
class ErrorMapper @Inject constructor() {

    fun map(throwable: Throwable): UciError = when (throwable) {
        is CancellationException -> throw throwable
        is SocketTimeoutException -> UciError.Timeout
        is UnknownHostException -> UciError.NoConnectivity
        is HttpException -> UciError.Http(code = throwable.code(), message = throwable.message())
        is SerializationException -> UciError.Serialization(throwable.message)
        is IOException -> UciError.NoConnectivity
        else -> UciError.Unexpected(throwable.message ?: throwable::class.simpleName)
    }
}
