package com.ulisescervera.uci.core.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.ulisescervera.uci.core.format.PropertyFormatter

/**
 * [PropertyFormatter] reaches Compose through a composition local.
 *
 * The alternatives were worse. Injecting `@ApplicationContext` into the
 * ViewModel so it could pre-format strings puts Android in a class that should
 * not know about it, and leaks the locale into a value that survives a
 * configuration change -- change the system language and the pre-formatted
 * strings would be stale. A `hiltViewModel()`-provided formatter would have the
 * same problem.
 *
 * A `staticCompositionLocalOf` provided by the hosting fragment keeps
 * formatting inside composition, so it re-runs when the configuration changes,
 * and it keeps the Compose screens usable from `@Preview` by providing a
 * formatter built from the preview context.
 *
 * `static` rather than `compositionLocalOf`: the value never changes within a
 * composition, so there is no point tracking reads.
 */
val LocalPropertyFormatter = staticCompositionLocalOf<PropertyFormatter> {
    error(
        "No PropertyFormatter provided. Wrap the content in " +
            "CompositionLocalProvider(LocalPropertyFormatter provides formatter).",
    )
}
