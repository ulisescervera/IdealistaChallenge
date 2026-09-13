package com.ulisescervera.uci.feature.list.filter

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.PropertyType
import com.ulisescervera.uci.support.AppFixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric only for [PropertyFilters.toBundle]/[PropertyFilters.fromBundle]:
 * `android.os.Bundle` is a framework class, and a plain JVM test would hit
 * "not mocked" the moment either function runs. The `matches` tests do not
 * need Android at all, but splitting them into a second, plain-JUnit class
 * for that reason would be more ceremony than the one Robolectric dependency
 * this file already needs anyway.
 */
@RunWith(RobolectricTestRunner::class)
class PropertyFiltersTest {

    @Test
    fun `no filters match anything`() {
        assertThat(PropertyFilters().matches(AppFixtures.property("1"))).isTrue()
    }

    @Test
    fun `a property type filter excludes every other type`() {
        val filters = PropertyFilters(propertyType = PropertyType.CHALET)
        val flat = AppFixtures.property("1", type = PropertyType.FLAT)
        val chalet = AppFixtures.property("2", type = PropertyType.CHALET)

        assertThat(filters.matches(flat)).isFalse()
        assertThat(filters.matches(chalet)).isTrue()
    }

    @Test
    fun `an operation filter excludes the other operation`() {
        val filters = PropertyFilters(operation = Operation.RENT)
        val sale = AppFixtures.property("1", operation = Operation.SALE)
        val rent = AppFixtures.property("2", operation = Operation.RENT)

        assertThat(filters.matches(sale)).isFalse()
        assertThat(filters.matches(rent)).isTrue()
    }

    @Test
    fun `a price range excludes properties outside it`() {
        val filters = PropertyFilters(minPrice = 100_000.0, maxPrice = 200_000.0)
        val cheap = AppFixtures.property("1", price = Money(50_000.0, "€"))
        val inRange = AppFixtures.property("2", price = Money(150_000.0, "€"))
        val expensive = AppFixtures.property("3", price = Money(500_000.0, "€"))

        assertThat(filters.matches(cheap)).isFalse()
        assertThat(filters.matches(inRange)).isTrue()
        assertThat(filters.matches(expensive)).isFalse()
    }

    @Test
    fun `a size range excludes properties outside it`() {
        val filters = PropertyFilters(minSize = 80.0, maxSize = 120.0)

        assertThat(filters.matches(AppFixtures.property("1", size = 50.0))).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", size = 100.0))).isTrue()
        assertThat(filters.matches(AppFixtures.property("3", size = 200.0))).isFalse()
    }

    @Test
    fun `an exact rooms bucket only matches that exact count`() {
        val filters = PropertyFilters(rooms = setOf(3))

        assertThat(filters.matches(AppFixtures.property("1", rooms = 2))).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", rooms = 3))).isTrue()
        assertThat(filters.matches(AppFixtures.property("3", rooms = 4))).isFalse()
    }

    @Test
    fun `the plus rooms bucket matches that count and above`() {
        val filters = PropertyFilters(rooms = setOf(PropertyFilters.ROOMS_PLUS_BUCKET))

        assertThat(filters.matches(AppFixtures.property("1", rooms = 4))).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", rooms = 5))).isTrue()
        assertThat(filters.matches(AppFixtures.property("3", rooms = 8))).isTrue()
    }

    @Test
    fun `the plus bathrooms bucket matches that count and above`() {
        val filters = PropertyFilters(bathrooms = setOf(PropertyFilters.BATHROOMS_PLUS_BUCKET))

        assertThat(filters.matches(AppFixtures.property("1", bathrooms = 3))).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", bathrooms = 4))).isTrue()
        assertThat(filters.matches(AppFixtures.property("3", bathrooms = 6))).isTrue()
    }

    @Test
    fun `requiring a lift rejects an unconfirmed property, not just a negative one`() {
        val filters = PropertyFilters(requiresLift = true)

        // Unconfirmed (null) must not be treated as "has a lift": the chip is an
        // explicit request, and a maybe does not satisfy it.
        assertThat(filters.matches(AppFixtures.property("1", hasLift = null))).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", hasLift = false))).isFalse()
        assertThat(filters.matches(AppFixtures.property("3", hasLift = true))).isTrue()
    }

    @Test
    fun `requiring a garage rejects unreported and absent parking alike`() {
        val filters = PropertyFilters(requiresGarage = true)

        assertThat(
            filters.matches(AppFixtures.property("1", parkingSpace = ParkingSpace.Unreported)),
        ).isFalse()
        assertThat(filters.matches(AppFixtures.property("2", parkingSpace = ParkingSpace.None))).isFalse()
        assertThat(
            filters.matches(AppFixtures.property("3", parkingSpace = ParkingSpace.Available(true))),
        ).isTrue()
    }

    @Test
    fun `energy certification and status are collected but never filter`() {
        // Documented limitation: Property (the list model) carries neither
        // field. See the class doc and docs/adr/0004.
        val filters = PropertyFilters(energyCertifications = setOf("A"), statuses = setOf("nuevo"))

        assertThat(filters.matches(AppFixtures.property("1"))).isTrue()
    }

    @Test
    fun `round-tripping through a bundle preserves every field`() {
        val filters = PropertyFilters(
            propertyType = PropertyType.CHALET,
            operation = Operation.RENT,
            minPrice = 100.0,
            maxPrice = 200.0,
            minSize = 10.0,
            maxSize = 20.0,
            rooms = setOf(2, PropertyFilters.ROOMS_PLUS_BUCKET),
            bathrooms = setOf(1),
            energyCertifications = setOf("A+", "B"),
            statuses = setOf("nuevo"),
            requiresLift = true,
            requiresGarage = true,
        )

        assertThat(PropertyFilters.fromBundle(filters.toBundle())).isEqualTo(filters)
    }

    @Test
    fun `an empty bundle round-trips to the default, empty filters`() {
        assertThat(PropertyFilters.fromBundle(PropertyFilters().toBundle())).isEqualTo(PropertyFilters())
    }
}
