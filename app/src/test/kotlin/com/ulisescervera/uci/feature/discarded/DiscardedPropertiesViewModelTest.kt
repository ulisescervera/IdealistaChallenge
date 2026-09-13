package com.ulisescervera.uci.feature.discarded

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.usecase.ObserveDiscardedPropertiesUseCase
import com.ulisescervera.uci.domain.usecase.RestoreAllDiscardedUseCase
import com.ulisescervera.uci.domain.usecase.RestorePropertyUseCase
import com.ulisescervera.uci.support.AppFixtures
import com.ulisescervera.uci.support.FakePropertyRepository
import com.ulisescervera.uci.support.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DiscardedPropertiesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val propertyRepository = FakePropertyRepository(
        mapOf("1" to PropertyFlag.DISCARDED, "2" to PropertyFlag.DISCARDED),
    )

    private fun viewModel() = DiscardedPropertiesViewModel(
        observeDiscardedProperties = ObserveDiscardedPropertiesUseCase(propertyRepository),
        restoreProperty = RestorePropertyUseCase(propertyRepository),
        restoreAllDiscarded = RestoreAllDiscardedUseCase(propertyRepository),
    )

    @Test
    fun `the empty copy waits for the first emission`() = runTest {
        val model = viewModel()

        // Otherwise the sheet flashes "no hay inmuebles borrados" as it opens.
        assertThat(model.state.value.hasLoaded).isFalse()
        assertThat(model.state.value.isEmpty).isFalse()
    }

    @Test
    fun `restoring one property writes NONE and confirms the count`() = runTest {
        val model = viewModel()
        propertyRepository.emitDiscarded(
            listOf(AppFixtures.discarded("1"), AppFixtures.discarded("2")),
        )

        model.effects.test {
            model.dispatch(DiscardedPropertiesIntent.RestoreClicked("1"))

            assertThat(awaitItem()).isEqualTo(DiscardedPropertiesEffect.Restored(count = 1))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(propertyRepository.writes).containsExactly("1" to PropertyFlag.NONE)
    }

    @Test
    fun `restore all reports how many came back`() = runTest {
        val model = viewModel()
        propertyRepository.emitDiscarded(
            listOf(AppFixtures.discarded("1"), AppFixtures.discarded("2")),
        )

        model.effects.test {
            model.dispatch(DiscardedPropertiesIntent.RestoreAllClicked)

            assertThat(awaitItem()).isEqualTo(DiscardedPropertiesEffect.Restored(count = 2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the sheet dismisses itself when the list empties out`() = runTest {
        val model = viewModel()
        propertyRepository.emitDiscarded(listOf(AppFixtures.discarded("1")))

        model.effects.test {
            propertyRepository.emitDiscarded(emptyList())

            // It was opened from a banner that no longer exists.
            assertThat(awaitItem()).isEqualTo(DiscardedPropertiesEffect.Dismiss)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an initially empty list does not dismiss the sheet before it is seen`() = runTest {
        val model = viewModel()

        model.effects.test {
            propertyRepository.emitDiscarded(emptyList())

            expectNoEvents()
        }
        assertThat(model.state.value.isEmpty).isTrue()
    }
}
