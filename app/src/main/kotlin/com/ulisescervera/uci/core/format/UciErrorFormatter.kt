package com.ulisescervera.uci.core.format

import android.content.Context
import com.ulisescervera.uci.R
import com.ulisescervera.uci.domain.common.UciError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place a [UciError] becomes a sentence a person reads.
 *
 * The `when` is exhaustive over a sealed interface, so adding a failure mode in
 * `:domain` fails the build here until somebody writes the copy for it. That is
 * the intended pressure: an unhandled error should be a compile error, not a
 * generic "algo ha ido mal" in production.
 *
 * The messages describe what happened and what the user can do. None of them
 * mentions HTTP, JSON or SQLite.
 */
@Singleton
class UciErrorFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun message(error: UciError): String = when (error) {
        UciError.NoConnectivity -> context.getString(R.string.uci_error_no_connectivity)
        UciError.Timeout -> context.getString(R.string.uci_error_timeout)
        is UciError.Http -> context.getString(R.string.uci_error_http, error.code)
        is UciError.Serialization -> context.getString(R.string.uci_error_serialization)
        is UciError.PropertyNotFound -> context.getString(R.string.uci_detail_error_not_found)
        is UciError.LocalStorage -> context.getString(R.string.uci_error_local)
        is UciError.Unexpected -> context.getString(R.string.uci_error_unexpected)
    }

    /** Whether the UI should offer a "retry" button next to the message. */
    fun isRetryable(error: UciError): Boolean = error.isRetryable
}
