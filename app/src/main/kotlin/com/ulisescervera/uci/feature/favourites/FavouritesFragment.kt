package com.ulisescervera.uci.feature.favourites

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.ulisescervera.uci.core.format.UciErrorFormatter
import com.ulisescervera.uci.core.ui.ComposeFragment
import com.ulisescervera.uci.core.ui.ext.collectWhileStarted
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Second tab, written in Compose as the brief requires.
 *
 * `collectAsStateWithLifecycle` rather than `collectAsState`, so the
 * Room-backed flow is unsubscribed while the tab sits in the background instead
 * of re-querying on every favourite the user toggles on the other tab.
 */
@AndroidEntryPoint
class FavouritesFragment : ComposeFragment() {

    private val viewModel: FavouritesViewModel by viewModels()

    @Inject lateinit var errorFormatter: UciErrorFormatter

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        FavouritesScreen(state = state, onIntent = viewModel::dispatch)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.effects.collectWhileStarted(viewLifecycleOwner) { effect ->
            when (effect) {
                is FavouritesEffect.OpenDetail -> findNavController().navigate(
                    FavouritesFragmentDirections.uciActionFavouritesToDetail(propertyId = effect.propertyId),
                )

                // A Toast rather than a Snackbar: there is no CoordinatorLayout
                // in a pure Compose screen to anchor one to, and adding a
                // SnackbarHost to the whole tab for a path that only fires on a
                // disk error is machinery for an edge case.
                is FavouritesEffect.ShowError -> Toast
                    .makeText(requireContext(), errorFormatter.message(effect.error), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}
