package com.ulisescervera.uci.domain.model

/**
 * Geolocation of a property. Only ever constructed through [orNull], because a
 * partial or out-of-range pair is worse than no pair at all: it would put a pin
 * in the Gulf of Guinea and the "see on map" button would look functional.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        private val LATITUDE_RANGE = -90.0..90.0
        private val LONGITUDE_RANGE = -180.0..180.0

        fun orNull(latitude: Double?, longitude: Double?): GeoPoint? {
            if (latitude == null || longitude == null) return null
            if (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE) return null
            if (latitude == 0.0 && longitude == 0.0) return null // null island
            return GeoPoint(latitude, longitude)
        }
    }
}
