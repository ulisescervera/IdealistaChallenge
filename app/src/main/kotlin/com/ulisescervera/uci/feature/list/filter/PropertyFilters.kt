package com.ulisescervera.uci.feature.list.filter

import android.os.Bundle
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyType

/**
 * Everything the filter sheet lets the user constrain, applied client-side
 * over the properties the list already has in memory -- there is no use case
 * or repository behind this, it is pure presentation state.
 *
 * ### Two fields exist in the sheet but never filter anything
 * [energyCertifications] and [statuses] are collected because the brief asks
 * for them, but [matches] never reads them. [Property] -- the list's model --
 * carries neither an energy rating nor a conservation status; only
 * `PropertyDetail`, fetched per-property on demand once its card is opened,
 * does. Filtering by them here would mean fetching the detail of every
 * visible property before the list can render a single row, which is exactly
 * the network-eager behaviour the offline-first repository exists to avoid.
 */
data class PropertyFilters(
    /** Empty means "any type". Checkboxes, not a single choice: see [matches]. */
    val propertyTypes: Set<PropertyType> = emptySet(),
    val operation: Operation? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val minSize: Double? = null,
    val maxSize: Double? = null,
    /** 2, 3, 4 or [ROOMS_PLUS_BUCKET]. Empty means "any". */
    val rooms: Set<Int> = emptySet(),
    /** 1, 2, 3 or [BATHROOMS_PLUS_BUCKET]. Empty means "any". */
    val bathrooms: Set<Int> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val requiresLift: Boolean = false,
    val requiresGarage: Boolean = false,
) {

    val isEmpty: Boolean get() = this == PropertyFilters()

    fun matches(property: Property): Boolean {
        if (propertyTypes.isNotEmpty() && property.propertyType !in propertyTypes) return false
        if (operation != null && property.operation != operation) return false
        if (minPrice != null && property.price.amount < minPrice) return false
        if (maxPrice != null && property.price.amount > maxPrice) return false
        if (minSize != null && property.sizeSquareMeters < minSize) return false
        if (maxSize != null && property.sizeSquareMeters > maxSize) return false
        if (!rooms.matchesBucketed(property.rooms, ROOMS_PLUS_BUCKET)) return false
        if (!bathrooms.matchesBucketed(property.bathrooms, BATHROOMS_PLUS_BUCKET)) return false
        // Unconfirmed (`null`) never satisfies "requires a lift": a chip the
        // user explicitly checked must not be honoured by a maybe.
        if (requiresLift && property.hasLift != true) return false
        if (requiresGarage && !property.parkingSpace.isAvailable) return false
        return true
    }

    fun toBundle(): Bundle = Bundle().apply {
        putStringArray(KEY_PROPERTY_TYPES, propertyTypes.map { it.name }.toTypedArray())
        putString(KEY_OPERATION, operation?.name)
        minPrice?.let { putDouble(KEY_MIN_PRICE, it) }
        maxPrice?.let { putDouble(KEY_MAX_PRICE, it) }
        minSize?.let { putDouble(KEY_MIN_SIZE, it) }
        maxSize?.let { putDouble(KEY_MAX_SIZE, it) }
        putIntArray(KEY_ROOMS, rooms.toIntArray())
        putIntArray(KEY_BATHROOMS, bathrooms.toIntArray())
        putStringArray(KEY_STATUSES, statuses.toTypedArray())
        putBoolean(KEY_LIFT, requiresLift)
        putBoolean(KEY_GARAGE, requiresGarage)
    }

    companion object {
        const val ROOMS_PLUS_BUCKET = 5
        const val BATHROOMS_PLUS_BUCKET = 4

        private const val KEY_PROPERTY_TYPES = "uci_filter_property_types"
        private const val KEY_OPERATION = "uci_filter_operation"
        private const val KEY_MIN_PRICE = "uci_filter_min_price"
        private const val KEY_MAX_PRICE = "uci_filter_max_price"
        private const val KEY_MIN_SIZE = "uci_filter_min_size"
        private const val KEY_MAX_SIZE = "uci_filter_max_size"
        private const val KEY_ROOMS = "uci_filter_rooms"
        private const val KEY_BATHROOMS = "uci_filter_bathrooms"
        private const val KEY_ENERGY = "uci_filter_energy"
        private const val KEY_STATUSES = "uci_filter_statuses"
        private const val KEY_LIFT = "uci_filter_lift"
        private const val KEY_GARAGE = "uci_filter_garage"

        fun fromBundle(bundle: Bundle): PropertyFilters = PropertyFilters(
            propertyTypes = bundle.getStringArray(KEY_PROPERTY_TYPES)
                ?.mapNotNull { runCatching { PropertyType.valueOf(it) }.getOrNull() }
                ?.toSet()
                .orEmpty(),
            operation = bundle.getString(KEY_OPERATION)
                ?.let { runCatching { Operation.valueOf(it) }.getOrNull() },
            minPrice = bundle.takeIf { it.containsKey(KEY_MIN_PRICE) }?.getDouble(KEY_MIN_PRICE),
            maxPrice = bundle.takeIf { it.containsKey(KEY_MAX_PRICE) }?.getDouble(KEY_MAX_PRICE),
            minSize = bundle.takeIf { it.containsKey(KEY_MIN_SIZE) }?.getDouble(KEY_MIN_SIZE),
            maxSize = bundle.takeIf { it.containsKey(KEY_MAX_SIZE) }?.getDouble(KEY_MAX_SIZE),
            rooms = bundle.getIntArray(KEY_ROOMS)?.toSet().orEmpty(),
            bathrooms = bundle.getIntArray(KEY_BATHROOMS)?.toSet().orEmpty(),
            statuses = bundle.getStringArray(KEY_STATUSES)?.toSet().orEmpty(),
            requiresLift = bundle.getBoolean(KEY_LIFT),
            requiresGarage = bundle.getBoolean(KEY_GARAGE),
        )
    }
}

/**
 * A bucketed selection matches when [count] equals a selected non-"plus"
 * bucket, or is at least [plusBucket] and that bucket is selected. Empty
 * selection means "any count".
 */
private fun Set<Int>.matchesBucketed(count: Int, plusBucket: Int): Boolean {
    if (isEmpty()) return true
    return any { bucket -> if (bucket == plusBucket) count >= bucket else count == bucket }
}
