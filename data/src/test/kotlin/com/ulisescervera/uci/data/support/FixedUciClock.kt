package com.ulisescervera.uci.data.support

import com.ulisescervera.uci.domain.common.UciClock
import java.time.Instant
import java.time.ZoneId

/**
 * A clock that does not move, so a test can assert an exact `cached_at` or an
 * exact favourite timestamp.
 *
 * [advanceTo] exists for the one test that needs two distinct instants (the
 * ordering of favourites by recency).
 */
class FixedUciClock(
    private var instant: Instant = Instant.parse("2026-03-03T18:12:00Z"),
    private var zone: ZoneId = ZoneId.of("Europe/Madrid"),
) : UciClock {

    override fun now(): Instant = instant

    override fun zone(): ZoneId = zone

    fun advanceTo(newInstant: Instant) {
        instant = newInstant
    }

    fun travelTo(newZone: ZoneId) {
        zone = newZone
    }
}
