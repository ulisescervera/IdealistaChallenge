package com.ulisescervera.uci.domain.common

/**
 * Closed vocabulary of everything that can go wrong in UCI.
 *
 * Mapping a `Throwable` to one of these is `:data`'s job (see `ErrorMapper`);
 * turning one of these into a human sentence is `:app`'s job. `:domain` only
 * guarantees the set is exhaustive, so a new failure mode cannot be forgotten
 * in a `when`.
 */
sealed interface UciError {

    /** No usable network interface, or the host could not be resolved. */
    data object NoConnectivity : UciError

    /** The request took longer than the configured budget. */
    data object Timeout : UciError

    /** Server answered, but not with 2xx. */
    data class Http(val code: Int, val message: String? = null) : UciError

    /** Server answered 2xx with a body we cannot parse into our DTOs. */
    data class Serialization(val message: String? = null) : UciError

    /** The requested property is not in the payload nor in the local cache. */
    data class PropertyNotFound(val propertyId: String) : UciError

    /** Room / disk failure. */
    data class LocalStorage(val message: String? = null) : UciError

    /** Anything we did not anticipate. Always worth logging. */
    data class Unexpected(val message: String? = null) : UciError

    /**
     * True when retrying the very same call has a reasonable chance of working.
     * Drives whether the UI offers a "retry" affordance.
     */
    val isRetryable: Boolean
        get() = when (this) {
            NoConnectivity, Timeout -> true
            is Http -> code >= 500 || code == 408 || code == 429
            is Serialization, is PropertyNotFound, is LocalStorage, is Unexpected -> false
        }
}
