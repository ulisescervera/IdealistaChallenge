package com.ulisescervera.uci.feature.map

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.ui.ViewBindingFragment
import com.ulisescervera.uci.databinding.UciFragmentFullMapBinding
import com.ulisescervera.uci.domain.model.GeoPoint
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The interactive map, alone, at full screen.
 *
 * Reached by tapping the detail's own map row. That row already shows an
 * interactive `MapView` (see `UciMapConfigurator.configureInteractive`), but it
 * is squeezed into a fixed-height card inside a scrolling list, competing with
 * the RecyclerView for vertical drags. This screen is the same configuration
 * given the whole viewport instead, with nothing else to compete with.
 *
 * A leaf destination like the image viewer: it does not appear in
 * `MainActivity`'s `DESTINATIONS_WITH_TOOLBAR`, so the shared toolbar hides
 * itself here and this fragment owns its own, exactly like the detail
 * screen's floating toolbar.
 */
@AndroidEntryPoint
class FullMapFragment : ViewBindingFragment<UciFragmentFullMapBinding>(
    UciFragmentFullMapBinding::inflate,
) {

    private val args: FullMapFragmentArgs by navArgs()

    @Inject lateinit var mapConfigurator: UciMapConfigurator

    override fun onBindingCreated(binding: UciFragmentFullMapBinding, savedInstanceState: Bundle?) {
        applyInsets()
        setUpToolbar()
        mapConfigurator.configureInteractive(
            binding.uciFullMapView,
            GeoPoint(args.latitude.toDouble(), args.longitude.toDouble()),
        )
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.uciFullMapToolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    private fun setUpToolbar() = with(binding.uciFullMapToolbar) {
        title = args.addressLine ?: getString(R.string.uci_full_map_title)
        setNavigationOnClickListener { findNavController().navigateUp() }
    }

    override fun onBindingDestroyed(binding: UciFragmentFullMapBinding) {
        mapConfigurator.release(binding.uciFullMapView)
    }
}
