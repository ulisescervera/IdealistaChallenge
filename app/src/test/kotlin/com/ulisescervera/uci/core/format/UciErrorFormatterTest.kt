package com.ulisescervera.uci.core.format

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class UciErrorFormatterTest {

    private val formatter = UciErrorFormatter(ApplicationProvider.getApplicationContext())

    @Test
    fun `every error has copy, and none of it mentions the transport`() {
        val errors = listOf(
            UciError.NoConnectivity,
            UciError.Timeout,
            UciError.Http(500),
            UciError.Serialization("boom"),
            UciError.PropertyNotFound("1"),
            UciError.LocalStorage(null),
            UciError.Unexpected(null),
        )

        errors.forEach { error ->
            val message = formatter.message(error)
            assertThat(message).isNotEmpty()
            // The user does not need to know about HTTP, JSON or SQLite.
            assertThat(message.lowercase()).doesNotContain("json")
            assertThat(message.lowercase()).doesNotContain("sqlite")
            assertThat(message.lowercase()).doesNotContain("exception")
        }
    }

    @Test
    fun `the http code is included so a bug report is actionable`() {
        assertThat(formatter.message(UciError.Http(503))).contains("503")
    }

    @Test
    fun `a missing property gets its own copy, not the generic one`() {
        assertThat(formatter.message(UciError.PropertyNotFound("7")))
            .isNotEqualTo(formatter.message(UciError.Unexpected(null)))
    }

    @Test
    fun `retryability is forwarded from the domain`() {
        assertThat(formatter.isRetryable(UciError.NoConnectivity)).isTrue()
        assertThat(formatter.isRetryable(UciError.PropertyNotFound("1"))).isFalse()
    }
}
