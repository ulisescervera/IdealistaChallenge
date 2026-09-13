package com.ulisescervera.uci.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces `Dispatchers.Main` for the duration of a test.
 *
 * `viewModelScope` is hardcoded to `Dispatchers.Main.immediate`, which does not
 * exist outside an Android runtime -- without this rule every ViewModel test
 * fails with "Module with the Main dispatcher had failed to initialize".
 *
 * [UnconfinedTestDispatcher] by default, so coroutines launched in an `init`
 * block have already run by the time the test body starts. That is what lets a
 * test assert the first state without an `advanceUntilIdle()` in every method.
 * Tests that need to control ordering pass a `StandardTestDispatcher` instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
