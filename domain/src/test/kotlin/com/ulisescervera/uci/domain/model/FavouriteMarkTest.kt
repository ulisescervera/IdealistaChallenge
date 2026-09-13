package com.ulisescervera.uci.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/**
 * The favourite mark is the one place UCI stores a timestamp, and the brief asks
 * specifically for the *time zone* to be stored with it. These tests pin down
 * why that matters.
 */
class FavouriteMarkTest {

    @Test
    fun `the wall clock is preserved in the zone the mark was created in`() {
        val instant = Instant.parse("2026-03-03T18:12:00Z")

        val mark = FavouriteMark.at(instant, ZoneId.of("Europe/Madrid"))
        val zoned = mark.atOriginalZone()

        // 18:12 UTC is 19:12 in Madrid in March (CET, +1).
        assertThat(zoned.hour).isEqualTo(19)
        assertThat(zoned.minute).isEqualTo(12)
        assertThat(zoned.zone).isEqualTo(ZoneId.of("Europe/Madrid"))
    }

    @Test
    fun `the same instant reads differently in a different stored zone`() {
        val instant = Instant.parse("2026-03-03T18:12:00Z")

        val madrid = FavouriteMark.at(instant, ZoneId.of("Europe/Madrid")).atOriginalZone()
        val mexico = FavouriteMark.at(instant, ZoneId.of("America/Mexico_City")).atOriginalZone()

        // Storing only the epoch would make both render identically, and the
        // user's "guardado a las 19:12" would change after a flight.
        assertThat(madrid.hour).isNotEqualTo(mexico.hour)
        assertThat(madrid.toInstant()).isEqualTo(mexico.toInstant())
    }

    @Test
    fun `an unknown zone id falls back to the system zone instead of throwing`() {
        // Zone databases change between Android versions; a mark written on a
        // device that knows "Europe/Kyiv" must still render on one that does not.
        val mark = FavouriteMark(epochMillis = 1_000L, zoneId = "Mars/Olympus_Mons")

        val zoned = mark.atOriginalZone()

        assertThat(zoned.zone).isEqualTo(ZoneId.systemDefault())
    }
}
