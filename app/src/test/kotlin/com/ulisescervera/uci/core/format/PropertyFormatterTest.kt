package com.ulisescervera.uci.core.format

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyType
import com.ulisescervera.uci.support.AppFixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The formatter, against real resources.
 *
 * Robolectric rather than a mocked `Context`: the point of these tests is that
 * the *strings* and *plurals* are right, and a mock that returns "" for
 * `getString` would pass every one of them.
 *
 * `@Config(qualifiers = "es")` pins the locale, so grouping separators are the
 * Spanish ones and a CI machine in another locale cannot change the result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class PropertyFormatterTest {

    private val formatter = PropertyFormatter(ApplicationProvider.getApplicationContext())

    // --------------------------------------------------------------- Title

    @Test
    fun `the title is type, zone and city as the brief specifies`() {
        val title = formatter.title(AppFixtures.property())

        assertThat(title).isEqualTo("Piso - Castellana - Madrid")
    }

    @Test
    fun `the district stands in when there is no neighbourhood`() {
        val title = formatter.title(
            PropertyType.FLAT,
            PropertyAddress(district = "Centro", municipality = "Madrid"),
        )

        assertThat(title).isEqualTo("Piso - Centro - Madrid")
    }

    @Test
    fun `a missing part collapses the separator instead of leaving a gap`() {
        // "Piso -  - Madrid" looks like a bug, because it is one.
        val title = formatter.title(PropertyType.FLAT, PropertyAddress(municipality = "Madrid"))

        assertThat(title).isEqualTo("Piso - Madrid")
        assertThat(title).doesNotContain("-  -")
    }

    @Test
    fun `with no address at all only the type is shown`() {
        assertThat(formatter.title(PropertyType.FLAT, PropertyAddress())).isEqualTo("Piso")
    }

    @Test
    fun `an unrecognised type still produces a readable title`() {
        val title = formatter.title(PropertyType.UNKNOWN, PropertyAddress(municipality = "Madrid"))

        assertThat(title).isEqualTo("Inmueble - Madrid")
    }

    // --------------------------------------------------------------- Prices

    @Test
    fun `the price keeps the suffix the backend sent`() {
        assertThat(formatter.price(Money(1_195_000.0, "€"))).contains("€")
        assertThat(formatter.price(Money(1_200.0, "€/mes"))).contains("€/mes")
    }

    @Test
    fun `the price is grouped and has no decimals`() {
        val price = formatter.price(Money(1_195_000.0, "€"))

        assertThat(price).contains("195")
        assertThat(price).doesNotContain(",00")
    }

    @Test
    fun `a zero price reads as on request rather than as free`() {
        assertThat(formatter.price(Money(0.0, "€"))).isEqualTo("Precio a consultar")
    }

    @Test
    fun `price per square meter uses the sale unit for a sale`() {
        val unit = formatter.pricePerSquareMeter(AppFixtures.property())

        assertThat(unit).isNotNull()
        assertThat(unit).contains("€/m²")
        assertThat(unit).doesNotContain("mes")
    }

    @Test
    fun `price per square meter uses the monthly unit for a rental`() {
        // Splicing "/m²" onto the API's "€/mes" would produce "€/mes/m²", which
        // reads as a monthly price per square metre per month.
        val unit = formatter.pricePerSquareMeter(
            AppFixtures.property(
                operation = Operation.RENT,
                price = Money(1_200.0, "€/mes"),
                size = 241.0,
            ),
        )

        assertThat(unit).isEqualTo("5 €/m²/mes")
    }

    @Test
    fun `price per square meter is null when the surface is unknown`() {
        assertThat(formatter.pricePerSquareMeter(AppFixtures.property(size = 0.0))).isNull()
    }

    // --------------------------------------------------------------- Facts

    @Test
    fun `a flat reports size, rooms, bathrooms, floor and lift`() {
        val labels = formatter.facts(AppFixtures.property(hasLift = true)).map { it.label }

        assertThat(labels).containsExactly(
            "133 m²",
            "3 habitaciones",
            "2 baños",
            "2ª planta",
            "Con ascensor",
        ).inOrder()
    }

    @Test
    fun `non-vertical housing reports neither floor nor lift`() {
        val labels = formatter.facts(
            AppFixtures.property(type = PropertyType.UNKNOWN, floor = Floor.NotApplicable),
        ).map { it.label }

        assertThat(labels).containsExactly("133 m²", "3 habitaciones", "2 baños").inOrder()
    }

    @Test
    fun `an unconfirmed lift has no chip at all`() {
        // The list endpoint never reports the lift, so an unopened property
        // genuinely does not know. Saying "sin ascensor" would be a factual
        // claim we cannot back up -- and a chip that only says "we don't know"
        // is not a fact about the property, so it does not get one either.
        val labels = formatter.facts(AppFixtures.property(hasLift = null)).map { it.label }

        assertThat(labels).doesNotContain("Sin ascensor")
        assertThat(labels).doesNotContain("Ascensor sin confirmar")
    }

    @Test
    fun `a confirmed lack of a lift is reported`() {
        val labels = formatter.facts(AppFixtures.property(hasLift = false)).map { it.label }

        assertThat(labels).contains("Sin ascensor")
    }

    @Test
    fun `a garage is reported and its inclusion in the price is distinguished`() {
        val included = formatter.facts(
            AppFixtures.property(parkingSpace = ParkingSpace.Available(isIncludedInPrice = true)),
        ).map { it.label }
        val extra = formatter.facts(
            AppFixtures.property(parkingSpace = ParkingSpace.Available(isIncludedInPrice = false)),
        ).map { it.label }

        assertThat(included).contains("Garaje incluido")
        assertThat(extra).contains("Garaje aparte")
    }

    @Test
    fun `an unreported garage produces no chip at all`() {
        val labels = formatter.facts(
            AppFixtures.property(parkingSpace = ParkingSpace.Unreported),
        ).map { it.label }

        assertThat(labels.none { it.contains("araje") }).isTrue()
    }

    @Test
    fun `singular and plural forms are both correct`() {
        val single = formatter.facts(AppFixtures.property(rooms = 1, bathrooms = 1)).map { it.label }

        assertThat(single).contains("1 habitación")
        assertThat(single).contains("1 baño")
    }

    @Test
    fun `zero rooms produces no chip rather than a chip saying zero`() {
        // "0 habitaciones" would read as "this flat has no bedrooms", which is
        // a claim, not an absence of data.
        val labels = formatter.facts(AppFixtures.property(rooms = 0, bathrooms = 0)).map { it.label }

        assertThat(labels.none { it.contains("habitaci") }).isTrue()
        assertThat(labels.none { it.contains("baño") }).isTrue()
    }

    @Test
    fun `the non numeric floor codes are localised`() {
        assertThat(formatter.floorLabel(Floor.Ground)).isEqualTo("Planta baja")
        assertThat(formatter.floorLabel(Floor.Basement)).isEqualTo("Sótano")
        assertThat(formatter.floorLabel(Floor.Mezzanine)).isEqualTo("Entreplanta")
        assertThat(formatter.floorLabel(Floor.Unknown("attic"))).isEqualTo("Planta attic")
    }

    @Test
    fun `no floor row for missing or inapplicable floors`() {
        assertThat(formatter.floorLabel(Floor.Missing)).isNull()
        assertThat(formatter.floorLabel(Floor.NotApplicable)).isNull()
    }

    // ------------------------------------------------------- Accessibility

    @Test
    fun `the card description carries the title, both prices and every fact`() {
        val description = formatter.accessibilityDescription(AppFixtures.property(hasLift = true))

        assertThat(description).contains("Piso - Castellana - Madrid")
        assertThat(description).contains("€")
        assertThat(description).contains("133 m²")
        assertThat(description).contains("Con ascensor")
    }

    @Test
    fun `an image description prefers the backend's own translation`() {
        val description = formatter.imageAccessibilityDescription(2, 7, "Cocina")

        assertThat(description).isEqualTo("Foto 3 de 7: Cocina")
    }

    @Test
    fun `an untagged image still says where the user is`() {
        val description = formatter.imageAccessibilityDescription(0, 7, null)

        assertThat(description).isEqualTo("Foto 1 de 7 del inmueble")
    }
}
