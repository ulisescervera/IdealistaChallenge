package com.ulisescervera.uci.data.network

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMapperTest {

    private val mapper = ErrorMapper()

    @Test
    fun `dns failure is reported as no connectivity`() {
        assertThat(mapper.map(UnknownHostException("idealista.github.io")))
            .isEqualTo(UciError.NoConnectivity)
    }

    @Test
    fun `socket timeout is reported as a timeout, not as offline`() {
        // The distinction matters: "sin conexión" and "ha tardado demasiado"
        // suggest different actions to the user.
        assertThat(mapper.map(SocketTimeoutException())).isEqualTo(UciError.Timeout)
    }

    @Test
    fun `any other io failure degrades to no connectivity`() {
        assertThat(mapper.map(IOException("broken pipe"))).isEqualTo(UciError.NoConnectivity)
    }

    @Test
    fun `http failures keep their status code`() {
        val exception = HttpException(
            Response.error<Unit>(503, "".toResponseBody("application/json".toMediaType())),
        )

        val error = mapper.map(exception)

        assertThat(error).isInstanceOf(UciError.Http::class.java)
        assertThat((error as UciError.Http).code).isEqualTo(503)
    }

    @Test
    fun `a malformed body is a serialization error`() {
        assertThat(mapper.map(SerializationException("unexpected token")))
            .isInstanceOf(UciError.Serialization::class.java)
    }

    @Test
    fun `an unrecognised throwable is unexpected but still carries its message`() {
        val error = mapper.map(IllegalStateException("something odd"))

        assertThat(error).isInstanceOf(UciError.Unexpected::class.java)
        assertThat((error as UciError.Unexpected).message).isEqualTo("something odd")
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, never reported as an error`() {
        // A cancelled coroutine is not a failure the user should read about, and
        // swallowing it here is how a ViewModel leaks a job past onCleared.
        mapper.map(CancellationException("scope closed"))
    }
}
