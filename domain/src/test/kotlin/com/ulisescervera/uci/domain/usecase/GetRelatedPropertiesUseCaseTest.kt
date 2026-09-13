package com.ulisescervera.uci.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.common.UciError
import com.ulisescervera.uci.domain.common.UciResult
import com.ulisescervera.uci.domain.common.getOrNull
import com.ulisescervera.uci.domain.fake.FakeRelatedPropertiesRepository
import com.ulisescervera.uci.domain.fake.PropertyFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetRelatedPropertiesUseCaseTest {

    @Test
    fun `the requested property is never returned even if the source includes it`() = runTest {
        // The fake deliberately misbehaves. The guarantee has to hold in the use
        // case, because a future real endpoint is outside our control.
        val repository = FakeRelatedPropertiesRepository().apply {
            returns(
                listOf(
                    PropertyFixtures.property(id = "1"),
                    PropertyFixtures.property(id = "2"),
                    PropertyFixtures.property(id = "3"),
                ),
            )
        }

        val result = GetRelatedPropertiesUseCase(repository)("1")

        assertThat(result.getOrNull()!!.map { it.id }).containsExactly("2", "3").inOrder()
    }

    @Test
    fun `the limit is respected after filtering, not before`() = runTest {
        val repository = FakeRelatedPropertiesRepository().apply {
            returns((1..10).map { PropertyFixtures.property(id = it.toString()) })
        }

        val result = GetRelatedPropertiesUseCase(repository)("1", limit = 3)

        val ids = result.getOrNull()!!.map { it.id }
        assertThat(ids).hasSize(3)
        assertThat(ids).doesNotContain("1")
    }

    @Test
    fun `the limit is forwarded to the repository`() = runTest {
        val repository = FakeRelatedPropertiesRepository()

        GetRelatedPropertiesUseCase(repository)("1", limit = 5)

        assertThat(repository.lastRequestedId).isEqualTo("1")
        assertThat(repository.lastRequestedLimit).isEqualTo(5)
    }

    @Test
    fun `a failure passes through untouched`() = runTest {
        val repository = FakeRelatedPropertiesRepository().apply { fails(UciError.Timeout) }

        val result = GetRelatedPropertiesUseCase(repository)("1")

        assertThat(result).isEqualTo(UciResult.Failure(UciError.Timeout))
    }
}
