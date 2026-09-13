package com.ulisescervera.uci.domain.model

/**
 * A discarded entry as the "undo" banner needs it: enough to name it in the
 * sheet, nothing more. Kept separate from [Property] so the banner does not
 * have to hold the full image list of every dismissal in memory.
 */
data class DiscardedProperty(
    val id: String,
    val propertyType: PropertyType,
    val zone: String?,
    val city: String?,
    val price: Money,
    val thumbnailUrl: String?,
    val discardedAtEpochMillis: Long,
)
