package com.ulisescervera.uci.domain.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * `isRetryable` decides whether the UI offers a retry button, so getting it
 * wrong means either a dead button or a missing escape hatch.
 */
class UciErrorTest {

    @Test
    fun `transport failures are retryable`() {
        assertThat(UciError.NoConnectivity.isRetryable).isTrue()
        assertThat(UciError.Timeout.isRetryable).isTrue()
    }

    @Test
    fun `server side http codes are retryable and client side ones are not`() {
        assertThat(UciError.Http(500).isRetryable).isTrue()
        assertThat(UciError.Http(503).isRetryable).isTrue()
        // Rate limiting and request timeout resolve themselves with time.
        assertThat(UciError.Http(429).isRetryable).isTrue()
        assertThat(UciError.Http(408).isRetryable).isTrue()

        // Retrying a 404 will produce another 404.
        assertThat(UciError.Http(404).isRetryable).isFalse()
        assertThat(UciError.Http(400).isRetryable).isFalse()
    }

    @Test
    fun `failures that will repeat identically are not retryable`() {
        assertThat(UciError.Serialization("boom").isRetryable).isFalse()
        assertThat(UciError.PropertyNotFound("7").isRetryable).isFalse()
        assertThat(UciError.LocalStorage(null).isRetryable).isFalse()
        assertThat(UciError.Unexpected(null).isRetryable).isFalse()
    }
}
