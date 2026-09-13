package com.ulisescervera.uci.feature.viewer

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.ui.ComposeFragment
import com.ulisescervera.uci.core.ui.ext.collectWhileStarted
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host of the Compose image viewer.
 *
 * The fragment exists for the one thing the composable cannot do on its own:
 * driving the window. Hiding the system bars is a *window* operation and the
 * `WindowInsetsController` belongs to the Activity, so the fragment mirrors
 * `state.showsSystemBars` onto it and puts everything back in
 * [onDestroyView].
 *
 * Restoring the bars on the way out is not optional: leaving them hidden would
 * strand the user on the detail screen with no status bar and no navigation
 * bar, which on a gesture-navigation device is close to unrecoverable.
 *
 * Rotation is deliberately *not* locked, and no `configChanges` is declared in
 * the manifest -- the brief requires this screen to rotate, and the ViewModel
 * already holds the mode state that has to survive it.
 */
@AndroidEntryPoint
class ImageViewerFragment : ComposeFragment() {

    private val viewModel: ImageViewerViewModel by viewModels()

    /** Only announce the immersive change on a real transition, not on every emission. */
    private var wasImmersive = false

    private val insetsController: WindowInsetsControllerCompat?
        get() = activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
        }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        ImageViewerScreen(state = state, onIntent = viewModel::dispatch)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }

        viewModel.state.collectWhileStarted(viewLifecycleOwner) { state ->
            val immersive = !state.showsSystemBars
            if (immersive != wasImmersive) {
                wasImmersive = immersive
                // Entering full screen is a big change that a sighted user sees
                // and a screen-reader user would not, so it is announced.
                announceImmersiveChange(immersive)
            }
            applySystemBarVisibility(visible = state.showsSystemBars)
        }
    }

    private fun announceImmersiveChange(immersive: Boolean) {
        view?.announceForAccessibility(
            getString(
                if (immersive) R.string.uci_a11y_immersive_on else R.string.uci_a11y_immersive_off,
            ),
        )
    }

    private fun applySystemBarVisibility(visible: Boolean) {
        val controller = insetsController ?: return
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, not the deprecated
            // "immersive sticky": a swipe from the edge brings the bars back
            // temporarily, so the user is never trapped in a screen with no
            // visible way out.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        // Always restore, whatever mode the user left in.
        applySystemBarVisibility(visible = true)
        super.onDestroyView()
    }
}
