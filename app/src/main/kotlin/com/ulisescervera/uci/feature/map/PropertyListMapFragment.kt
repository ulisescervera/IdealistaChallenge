package com.ulisescervera.uci.feature.map

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.ulisescervera.uci.core.map.UciMapConfigurator
import com.ulisescervera.uci.core.map.UciMapMarker
import com.ulisescervera.uci.core.ui.ViewBindingFragment
import com.ulisescervera.uci.databinding.UciFragmentPropertyListMapBinding
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.feature.list.PropertyListViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * "All properties on a map", reached from the list's toolbar map icon.
 *
 * Shares [PropertyListViewModel] with `PropertyListFragment` (both are
 * activity-scoped) instead of receiving the properties as navigation
 * arguments: SafeArgs has no clean way to carry a `List<Property>`, and this
 * way a favourite toggled from the list is already reflected if the user
 * backs out and reopens the map, no re-fetch involved.
 *
 * The snapshot is read once, on creation, exactly like `FullMapFragment`'s
 * single marker: this screen has no loading state of its own to react to,
 * and re-drawing every marker on every list emission would fight the user's
 * own pan and zoom.
 */
@AndroidEntryPoint
class PropertyListMapFragment : ViewBindingFragment<UciFragmentPropertyListMapBinding>(
    UciFragmentPropertyListMapBinding::inflate,
) {

    private val viewModel: PropertyListViewModel by activityViewModels()

    @Inject lateinit var mapConfigurator: UciMapConfigurator

    override fun onBindingCreated(binding: UciFragmentPropertyListMapBinding, savedInstanceState: Bundle?) {
        applyInsets()
        setUpToolbar()
        setUpMarkers()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.uciListMapToolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    private fun setUpToolbar() {
        binding.uciListMapToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setUpMarkers() {
        val properties = viewModel.state.value.visibleProperties.filter(Property::hasLocation)
        mapConfigurator.configureMultiple(
            mapView = binding.uciListMapView,
            markers = properties.map { UciMapMarker(id = it.id, location = checkNotNull(it.location)) },
            onMarkerClicked = ::openDetail,
        )
    }

    private fun openDetail(propertyId: String) {
        findNavController().navigate(
            PropertyListMapFragmentDirections.uciActionListMapToDetail(propertyId = propertyId),
        )
    }

    override fun onBindingDestroyed(binding: UciFragmentPropertyListMapBinding) {
        mapConfigurator.release(binding.uciListMapView)
    }
}
