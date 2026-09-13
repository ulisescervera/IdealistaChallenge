package com.ulisescervera.uci.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the user marked a property as favourite, *and in which time zone*.
 *
 * Storing only the epoch would make "guardado a las 19:12" drift if the user
 * flies to another country; storing only the local text would make sorting
 * impossible. We keep both and reconstruct a [ZonedDateTime] on demand.
 */
data class FavouriteMark(
    val epochMillis: Long,
    val zoneId: String,
) {
    /** The original wall-clock moment, in the zone it was created in. */
    fun atOriginalZone(): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), resolvedZone())

    private fun resolvedZone(): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.systemDefault() }

    companion object {
        fun at(instant: Instant, zone: ZoneId): FavouriteMark =
            FavouriteMark(epochMillis = instant.toEpochMilli(), zoneId = zone.id)
    }
}
