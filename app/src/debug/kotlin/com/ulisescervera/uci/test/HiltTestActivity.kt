package com.ulisescervera.uci.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty, Hilt-enabled Activity for hosting a single fragment under test.
 *
 * `launchFragmentInContainer` from `fragment-testing` uses its own container
 * activity, which is *not* annotated with `@AndroidEntryPoint` -- so a fragment
 * that needs field injection crashes with "Hilt Fragment must be attached to an
 * @AndroidEntryPoint Activity". This class is the documented workaround.
 *
 * It lives in the `debug` source set rather than `androidTest` because the
 * manifest that declares it has to be merged into the *app under test*, not into
 * the test APK.
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The real app theme, so a test sees the real colours, the real
        // typography and the real touch targets.
        setTheme(com.ulisescervera.uci.R.style.Theme_Uci)
        super.onCreate(savedInstanceState)
    }
}
