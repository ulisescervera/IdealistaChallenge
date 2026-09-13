package com.ulisescervera.uci

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps [UciApplication] for [HiltTestApplication] during instrumented tests.
 *
 * Without this, Hilt's test rule has no component to install test bindings into
 * and every `@HiltAndroidTest` fails at startup. Referenced from
 * `app/build.gradle.kts` as the `testInstrumentationRunner`.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
