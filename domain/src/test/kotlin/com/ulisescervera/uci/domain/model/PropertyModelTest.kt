package com.ulisescervera.uci.domain.model

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.fake.PropertyFixtures
import org.junit.Test

/**
 * The invariants of the domain models.
 *
 * These are the rules the rest of the app is allowed to assume, so they are
 * tested directly rather than through a repository: if `Floor.from("bj")` ever
 * stops meaning "planta baja", this file should be the thing that fails.
 */
class PropertyModelTest {

    // ---------------------------------------------------------------- PropertyType

    @Test
    fun `property type maps every api value it claims to support`() {
        assertThat(PropertyType.from("flat")).isEqualTo(PropertyType.FLAT)
    }

    @Test
    fun `property type is case insensitive because the backend is not consistent`() {
        assertThat(PropertyType.from("FLAT")).isEqualTo(PropertyType.FLAT)
        assertThat(PropertyType.from("Flat")).isEqualTo(PropertyType.FLAT)
    }

    @Test
    fun `unknown property type degrades instead of throwing`() {
        // A new backend value must never drop a property from the list.
        assertThat(PropertyType.from("houseboat")).isEqualTo(PropertyType.UNKNOWN)
        assertThat(PropertyType.from(null)).isEqualTo(PropertyType.UNKNOWN)
    }

    @Test
    fun `only multi storey housing reports floor and lift`() {
        assertThat(PropertyType.FLAT.isVerticalHousing).isTrue()
        assertThat(PropertyType.UNKNOWN.isVerticalHousing).isFalse()
    }

    // --------------------------------------------------------------------- Floor

    @Test
    fun `floor parses the non numeric codes the api uses`() {
        assertThat(Floor.from("bj", PropertyType.FLAT)).isEqualTo(Floor.Ground)
        assertThat(Floor.from("ss", PropertyType.FLAT)).isEqualTo(Floor.Basement)
        assertThat(Floor.from("en", PropertyType.FLAT)).isEqualTo(Floor.Mezzanine)
        assertThat(Floor.from("2", PropertyType.FLAT)).isEqualTo(Floor.Numbered(2))
    }

    @Test
    fun `floor is not applicable for housing without storeys`() {
        // Non-vertical housing has no "floor" to report, which is different from not knowing.
        assertThat(Floor.from("2", PropertyType.UNKNOWN)).isEqualTo(Floor.NotApplicable)
    }

    @Test
    fun `absent floor is missing and unrecognised floor keeps its raw text`() {
        assertThat(Floor.from(null, PropertyType.FLAT)).isEqualTo(Floor.Missing)
        assertThat(Floor.from("  ", PropertyType.FLAT)).isEqualTo(Floor.Missing)
        assertThat(Floor.from("attic", PropertyType.FLAT)).isEqualTo(Floor.Unknown("attic"))
    }

    // ------------------------------------------------------------------ GeoPoint

    @Test
    fun `geo point rejects partial and impossible coordinates`() {
        assertThat(GeoPoint.orNull(40.4, null)).isNull()
        assertThat(GeoPoint.orNull(null, -3.6)).isNull()
        assertThat(GeoPoint.orNull(120.0, -3.6)).isNull()
        assertThat(GeoPoint.orNull(40.4, 200.0)).isNull()
    }

    @Test
    fun `geo point rejects null island`() {
        // 0,0 is what a backend sends when it means "no idea"; putting a pin in
        // the Gulf of Guinea is worse than hiding the map button.
        assertThat(GeoPoint.orNull(0.0, 0.0)).isNull()
    }

    @Test
    fun `geo point accepts a real madrid coordinate`() {
        assertThat(GeoPoint.orNull(40.4362687, -3.6833686))
            .isEqualTo(GeoPoint(40.4362687, -3.6833686))
    }

    // --------------------------------------------------------------------- Money

    @Test
    fun `price per square meter divides and keeps the suffix`() {
        val unit = Money(1_195_000.0, "€").perSquareMeter(133.0)
        assertThat(unit).isNotNull()
        assertThat(unit!!.amount).isWithin(TOLERANCE).of(8_984.96)
        assertThat(unit.currencySuffix).isEqualTo("€")
    }

    @Test
    fun `price per square meter is null for an unknown surface`() {
        // Returning Infinity here would reach the UI as "∞ €/m²".
        assertThat(Money(100.0, "€").perSquareMeter(0.0)).isNull()
        assertThat(Money(100.0, "€").perSquareMeter(-5.0)).isNull()
    }

    // ---------------------------------------------------------------- PropertyFlag

    @Test
    fun `favourite and discarded are exclusive by construction`() {
        assertThat(PropertyFlag.FAVOURITE.isDiscarded).isFalse()
        assertThat(PropertyFlag.DISCARDED.isFavourite).isFalse()
    }

    @Test
    fun `toggling favourite twice returns to none`() {
        val once = PropertyFlag.NONE.toggledFavourite()
        assertThat(once).isEqualTo(PropertyFlag.FAVOURITE)
        assertThat(once.toggledFavourite()).isEqualTo(PropertyFlag.NONE)
    }

    @Test
    fun `favouriting a discarded property un discards it`() {
        assertThat(PropertyFlag.DISCARDED.toggledFavourite()).isEqualTo(PropertyFlag.FAVOURITE)
    }

    // ------------------------------------------------------------------- Property

    @Test
    fun `has location drives the see on map button`() {
        assertThat(PropertyFixtures.property().hasLocation).isTrue()
        assertThat(PropertyFixtures.property(location = null).hasLocation).isFalse()
    }

    @Test
    fun `carousel falls back to the thumbnail when multimedia is empty`() {
        val property = PropertyFixtures.property(images = emptyList())
        assertThat(property.carouselImages).hasSize(1)
        assertThat(property.carouselImages.single().url).isEqualTo(property.thumbnailUrl)
    }

    @Test
    fun `carousel prefers multimedia over the thumbnail to avoid duplicates`() {
        val property = PropertyFixtures.property()
        assertThat(property.carouselImages).hasSize(2)
        assertThat(property.carouselImages.map { it.url }).doesNotContain(property.thumbnailUrl)
    }

    @Test
    fun `clearing the favourite flag drops the favourite mark`() {
        val mark = FavouriteMark(epochMillis = 1_000L, zoneId = "Europe/Madrid")
        val favourite = PropertyFixtures.property(flag = PropertyFlag.FAVOURITE, favouriteMark = mark)

        val cleared = favourite.withFlag(PropertyFlag.NONE, mark)

        // A timestamp next to a non-favourite would be a lie.
        assertThat(cleared.favouriteMark).isNull()
    }

    private companion object {
        const val TOLERANCE = 0.01
    }
}
