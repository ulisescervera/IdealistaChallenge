package com.ulisescervera.uci.domain.common

/**
 * The single result type crossing the repository boundary.
 *
 * `:domain` deliberately does not expose `kotlin.Result`: it cannot be used as a
 * return type of a suspend function without boxing surprises, and it would let
 * arbitrary [Throwable]s from Retrofit or Room leak into the presentation layer.
 * [UciError] keeps the failure vocabulary closed and translatable.
 */
sealed interface UciResult<out T> {

    data class Success<out T>(val value: T) : UciResult<T>

    data class Failure(val error: UciError) : UciResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

inline fun <T, R> UciResult<T>.map(transform: (T) -> R): UciResult<R> = when (this) {
    is UciResult.Success -> UciResult.Success(transform(value))
    is UciResult.Failure -> this
}

inline fun <T, R> UciResult<T>.flatMap(transform: (T) -> UciResult<R>): UciResult<R> = when (this) {
    is UciResult.Success -> transform(value)
    is UciResult.Failure -> this
}

fun <T> UciResult<T>.getOrNull(): T? = (this as? UciResult.Success)?.value

fun <T> UciResult<T>.errorOrNull(): UciError? = (this as? UciResult.Failure)?.error

inline fun <T> UciResult<T>.onSuccess(action: (T) -> Unit): UciResult<T> = apply {
    if (this is UciResult.Success) action(value)
}

inline fun <T> UciResult<T>.onFailure(action: (UciError) -> Unit): UciResult<T> = apply {
    if (this is UciResult.Failure) action(error)
}

inline fun <T, R> UciResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (UciError) -> R,
): R = when (this) {
    is UciResult.Success -> onSuccess(value)
    is UciResult.Failure -> onFailure(error)
}

fun <T> T.asSuccess(): UciResult<T> = UciResult.Success(this)

fun UciError.asFailure(): UciResult<Nothing> = UciResult.Failure(this)
