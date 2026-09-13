package com.ulisescervera.uci.domain.model

/**
 * Optional amenities. Every field is nullable on purpose: the list endpoint
 * omits keys per property (property 4 sends `hasGarden`, property 1 does not),
 * and `false` ("no garden") must not be confused with `null` ("not reported").
 */
data class PropertyFeatures(
    val hasAirConditioning: Boolean? = null,
    val hasBoxRoom: Boolean? = null,
    val hasSwimmingPool: Boolean? = null,
    val hasTerrace: Boolean? = null,
    val hasGarden: Boolean? = null,
) {
    val reportedCount: Int
        get() = listOf(hasAirConditioning, hasBoxRoom, hasSwimmingPool, hasTerrace, hasGarden)
            .count { it != null }

    companion object {
        val Empty = PropertyFeatures()
    }
}
