package com.ulisescervera.uci.domain.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UciResultTest {

    @Test
    fun `map transforms a success and leaves a failure untouched`() {
        assertThat(UciResult.Success(2).map { it * 3 }.getOrNull()).isEqualTo(6)

        val failure: UciResult<Int> = UciResult.Failure(UciError.Timeout)
        assertThat(failure.map { it * 3 }.errorOrNull()).isEqualTo(UciError.Timeout)
    }

    @Test
    fun `flatMap short circuits on the first failure`() {
        var secondRan = false

        val result = UciResult.Failure(UciError.NoConnectivity)
            .flatMap<Nothing, Int> { secondRan = true; UciResult.Success(1) }

        assertThat(secondRan).isFalse()
        assertThat(result.errorOrNull()).isEqualTo(UciError.NoConnectivity)
    }

    @Test
    fun `fold picks exactly one branch`() {
        val fromSuccess = UciResult.Success("ok").fold(onSuccess = { it }, onFailure = { "error" })
        val fromFailure = UciResult.Failure(UciError.Timeout)
            .fold(onSuccess = { "ok" }, onFailure = { "error" })

        assertThat(fromSuccess).isEqualTo("ok")
        assertThat(fromFailure).isEqualTo("error")
    }

    @Test
    fun `onSuccess and onFailure run only for their own case`() {
        var successes = 0
        var failures = 0

        UciResult.Success(Unit).onSuccess { successes++ }.onFailure { failures++ }
        UciResult.Failure(UciError.Timeout).onSuccess { successes++ }.onFailure { failures++ }

        assertThat(successes).isEqualTo(1)
        assertThat(failures).isEqualTo(1)
    }
}
