package com.ulisescervera.uci.support

import android.content.ComponentName
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ulisescervera.uci.test.HiltTestActivity

/**
 * Launches a fragment inside [HiltTestActivity] so field injection works.
 *
 * The stock `launchFragmentInContainer` uses a container activity that is not
 * `@AndroidEntryPoint`, which makes any Hilt fragment throw on attach. This is
 * the standard replacement: an explicit intent at the debug-only test activity,
 * then a manual `commitNow`.
 *
 * The theme is not passed as an intent extra -- that is `FragmentScenario`'s
 * protocol, and [HiltTestActivity] applies `Theme.Uci` itself in `onCreate`.
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: android.os.Bundle? = null,
    fragmentFactory: FragmentFactory? = null,
    crossinline action: T.() -> Unit = {},
) {
    val startActivityIntent = Intent.makeMainActivity(
        ComponentName(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            HiltTestActivity::class.java,
        ),
    )

    ActivityScenario.launch<HiltTestActivity>(startActivityIntent).onActivity { activity ->
        fragmentFactory?.let { activity.supportFragmentManager.fragmentFactory = it }
        val fragment = activity.supportFragmentManager.fragmentFactory.instantiate(
            checkNotNull(T::class.java.classLoader) { "No class loader for ${T::class.java.name}" },
            T::class.java.name,
        )
        fragment.arguments = fragmentArgs

        activity.supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(android.R.id.content, fragment, "")
        }

        (fragment as T).action()
    }
}
