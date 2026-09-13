package com.ulisescervera.uci.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.ulisescervera.uci.core.compose.LocalPropertyFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.theme.UciTheme
import javax.inject.Inject

/**
 * Base class for the two Compose screens (image viewer, favourites).
 *
 * UCI is view-based with Compose islands, not the other way round: the
 * navigation graph, the back stack and the bottom bar are all
 * Fragment/Navigation concerns, and the Compose screens plug into them through
 * a `ComposeView`. That keeps one navigation model instead of nesting a
 * `NavHost` inside a fragment inside a graph.
 *
 * Two things are set up once here so neither screen can forget them:
 *
 * - **[ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed].** The
 *   default strategy disposes the composition when the *view* detaches, which
 *   for a fragment going onto the back stack happens on every navigation --
 *   losing every `remember`ed value, including scroll positions. Tying disposal
 *   to the view-tree lifecycle keeps them.
 * - **[LocalPropertyFormatter].** Both screens format properties, and both must
 *   do it with the same formatter the XML list uses. Providing it in the base
 *   class is what guarantees they cannot drift.
 *
 * The field is injected even though it is declared here: Hilt injects inherited
 * `@Inject` fields as long as the concrete subclass carries
 * `@AndroidEntryPoint`.
 */
abstract class ComposeFragment : Fragment() {

    @Inject lateinit var propertyFormatter: PropertyFormatter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            UciTheme {
                CompositionLocalProvider(LocalPropertyFormatter provides propertyFormatter) {
                    ScreenContent()
                }
            }
        }
    }

    /** The screen. Already wrapped in [UciTheme] and the composition locals. */
    @Composable
    protected abstract fun ScreenContent()
}
