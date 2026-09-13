package com.ulisescervera.uci.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.common.getOrNull
import com.ulisescervera.uci.domain.fake.FakePropertyRepository
import com.ulisescervera.uci.domain.model.PropertyFlag
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The four use cases that write the user's own state. Between them they encode
 * the whole favourite/discarded behaviour the brief describes, so this is the
 * file to read to understand it.
 */
class FlagUseCasesTest {

    @Test
    fun `toggling favourite reads the current state instead of trusting the caller`() = runTest {
        // Two screens can show the same property at once; a stale UI state must
        // not be able to decide the outcome.
        val repository = FakePropertyRepository(mapOf("1" to PropertyFlag.FAVOURITE))
        val toggle = ToggleFavouriteUseCase(repository)

        val result = toggle("1")

        assertThat(result.getOrNull()).isEqualTo(PropertyFlag.NONE)
        assertThat(repository.currentFlags()).doesNotContainKey("1")
    }

    @Test
    fun `toggling favourite on an unflagged property favourites it`() = runTest {
        val repository = FakePropertyRepository()

        val result = ToggleFavouriteUseCase(repository)("1")

        assertThat(result.getOrNull()).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(repository.currentFlags()["1"]).isEqualTo(PropertyFlag.FAVOURITE)
    }

    @Test
    fun `favouriting a discarded property restores it in the same write`() = runTest {
        val repository = FakePropertyRepository(mapOf("1" to PropertyFlag.DISCARDED))

        ToggleFavouriteUseCase(repository)("1")

        // One column, one write: the illegal "favourite and discarded" state is
        // not reachable.
        assertThat(repository.currentFlags()["1"]).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(repository.writes).containsExactly("1" to PropertyFlag.FAVOURITE)
    }

    @Test
    fun `discarding is idempotent because the list button is one way`() = runTest {
        val repository = FakePropertyRepository()
        val discard = DiscardPropertyUseCase(repository)

        discard("1")
        discard("1")

        // A double tap on "remove" must not silently re-add the property.
        assertThat(repository.currentFlags()["1"]).isEqualTo(PropertyFlag.DISCARDED)
        assertThat(repository.writes).hasSize(2)
        assertThat(repository.writes.map { it.second }.toSet())
            .containsExactly(PropertyFlag.DISCARDED)
    }

    @Test
    fun `restoring clears the flag entirely`() = runTest {
        val repository = FakePropertyRepository(mapOf("1" to PropertyFlag.DISCARDED))

        RestorePropertyUseCase(repository)("1")

        assertThat(repository.currentFlags()).isEmpty()
    }

    @Test
    fun `restore all reports how many came back and leaves favourites alone`() = runTest {
        val repository = FakePropertyRepository(
            mapOf(
                "1" to PropertyFlag.DISCARDED,
                "2" to PropertyFlag.DISCARDED,
                "3" to PropertyFlag.FAVOURITE,
            ),
        )

        val result = RestoreAllDiscardedUseCase(repository)()

        assertThat(result.getOrNull()).isEqualTo(2)
        assertThat(repository.currentFlags()).containsExactly("3", PropertyFlag.FAVOURITE)
    }

    @Test
    fun `a failed write surfaces as a failure and changes nothing`() = runTest {
        val repository = FakePropertyRepository().apply {
            failWith = UciError.LocalStorage("disk full")
        }

        val result = ToggleFavouriteUseCase(repository)("1")

        assertThat(result).isInstanceOf(UciResult.Failure::class.java)
        assertThat(repository.currentFlags()).isEmpty()
    }
}
