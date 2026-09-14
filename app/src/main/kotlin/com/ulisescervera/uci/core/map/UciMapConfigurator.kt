package com.ulisescervera.uci.core.map

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ulisescervera.uci.R
import com.ulisescervera.uci.domain.model.GeoPoint as UciGeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * All osmdroid knowledge in one class.
 *
 * OpenStreetMap was chosen over the Google Maps SDK for one decisive reason: it
 * needs no API key, so this repository builds and shows a real map the moment it
 * is cloned. A Google Maps build without a key compiles fine and then shows a
 * grey rectangle, which is the worst possible failure mode for a reviewer.
 *
 * Three configurations, one per shape of "how many pins, how much gesture":
 *
 * - [configureStatic] -- no gestures at all. Inside a `ViewPager2` page, a map
 *   that consumes horizontal drags makes the carousel feel broken.
 * - [configureInteractive] -- pan and zoom, for the map row of the detail
 *   screen, where the user is deliberately exploring.
 * - [configureMultiple] -- pan and zoom over several pins at once, for the
 *   list's "all properties on a map" screen.
 *
 * [release] must be called when a page is recycled or a fragment's view is
 * destroyed: a `MapView` owns a tile-download executor and a tile cache, and
 * `onDetach` is the only thing that stops them.
 */
@Singleton
class UciMapConfigurator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Fixed-colour marker, not a theme-tinted one: Mapnik tiles are light in
     * both schemes, so a `?attr/colorOnSurfaceVariant` pin would turn pale grey
     * in dark mode and disappear against the map.
     */
    private val markerIcon: Drawable? by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_uci_map_marker)
    }

    fun configureStatic(mapView: MapView, location: UciGeoPoint) {
        mapView.applyCommonConfiguration(location, zoom = STATIC_ZOOM)
        mapView.setMultiTouchControls(false)
        mapView.setBuiltInZoomControls(false)
        // Both must be off: `setScrollableAreaLimit` alone still lets the map
        // swallow the ViewPager2 drag before deciding it cannot scroll.
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false
        mapView.isClickable = false
        mapView.isFocusable = false
    }

    fun configureInteractive(mapView: MapView, location: UciGeoPoint) {
        mapView.applyCommonConfiguration(location, zoom = INTERACTIVE_ZOOM)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        // The map lives inside a vertical RecyclerView; without this the parent
        // steals every vertical drag and the map can only be panned sideways.
        mapView.requestDisallowInterceptTouchEvent(true)
    }

    /**
     * One marker per [markers] entry, for the "all properties on a map" screen.
     * A single-property map centres and zooms to a fixed street level; with
     * several pins that fixed zoom would as easily crop half of them as show
     * them all, so the camera instead fits a [BoundingBox] around every point.
     */
    fun configureMultiple(mapView: MapView, markers: List<UciMapMarker>, onMarkerClicked: (String) -> Unit) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setUseDataConnection(true)
        mapView.isTilesScaledToDpi = true
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.overlays.clear()

        val points = markers.map { GeoPoint(it.location.latitude, it.location.longitude) }
        markers.forEachIndexed { index, marker ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = points[index]
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = markerIcon
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> onMarkerClicked(marker.id).let { true } }
                },
            )
        }

        when (points.size) {
            0 -> Unit
            1 -> {
                mapView.controller.setZoom(STATIC_ZOOM)
                mapView.controller.setCenter(points.first())
            }
            // Needs a laid-out view to know its own pixel size; posting is what
            // the boundless single-point branch above does not require.
            else -> mapView.post {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, BOUNDING_BOX_PADDING_PX)
            }
        }
        mapView.invalidate()
    }

    private fun MapView.applyCommonConfiguration(location: UciGeoPoint, zoom: Double) {
        setTileSource(TileSourceFactory.MAPNIK)
        setUseDataConnection(true)
        // Tiles are square and low-detail when scaled; letting osmdroid pick the
        // density-appropriate tile size keeps labels legible on xxhdpi.
        isTilesScaledToDpi = true
        overlays.clear()

        val point = GeoPoint(location.latitude, location.longitude)
        controller.setZoom(zoom)
        controller.setCenter(point)

        overlays.add(
            Marker(this).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = markerIcon
                // The map itself is marked importantForAccessibility=no and the
                // container carries the description, so an infowindow here would
                // only be reachable by sighted users anyway.
                infoWindow = null
            },
        )
        invalidate()
    }

    /** Stops the tile threads. Call from `onViewRecycled` / `onDestroyView`. */
    fun release(mapView: MapView) {
        mapView.overlays.clear()
        mapView.onDetach()
    }

    private companion object {
        /** Street level: enough context to recognise the neighbourhood. */
        const val STATIC_ZOOM = 15.5
        const val INTERACTIVE_ZOOM = 16.0
        const val BOUNDING_BOX_PADDING_PX = 64
    }
}

/** A pin on the "all properties" map: [id] travels back through the click to open its detail. */
data class UciMapMarker(val id: String, val location: UciGeoPoint)
