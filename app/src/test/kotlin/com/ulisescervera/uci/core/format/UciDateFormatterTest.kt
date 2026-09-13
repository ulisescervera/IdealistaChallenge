package com.ulisescervera.uci.core.format

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.FavouriteMark
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class UciDateFormatterTest {

    private val formatter = UciDateFormatter(ApplicationProvider.getApplicationContext())

    @Test
    fun `the favourite date is rendered in the zone it was stored with`() {
        val mark = FavouriteMark.at(
            Instant.parse("2026-03-03T18:12:00Z"),
            ZoneId.of("Europe/Madrid"),
        )

        val text = formatter.favouriteMark(mark)

        // 19:12 in Madrid, not 18:12 UTC and not the device's zone.
        assertThat(text).contains("19:12")
        assertThat(text).contains("2026")
    }

    @Test
    fun `the same instant reads differently for a mark made in another zone`() {
        val instant = Instant.parse("2026-03-03T18:12:00Z")

        val madrid = formatter.favouriteMark(FavouriteMark.at(instant, ZoneId.of("Europe/Madrid")))
        val mexico = formatter.favouriteMark(
            FavouriteMark.at(instant, ZoneId.of("America/Mexico_City")),
        )

        assertThat(madrid).isNotEqualTo(mexico)
    }

    @Test
    fun `modification dates render as a plain localised date`() {
        val text = formatter.epochMillisAsDate(1_727_683_968_000)

        assertThat(text).contains("2024")
    }
}
