package com.ulisescervera.uci.domain.common

import java.time.Instant
import java.time.ZoneId

/**
 * Time is an input, not an ambient fact. Favourite marks store both the instant
 * and the zone the user was in when they tapped, so the detail screen can show
 * "guardado el 3 de marzo a las 19:12" in the original zone even if the device
 * later travels.
 */
interface UciClock {
    fun now(): Instant
    fun zone(): ZoneId
}
