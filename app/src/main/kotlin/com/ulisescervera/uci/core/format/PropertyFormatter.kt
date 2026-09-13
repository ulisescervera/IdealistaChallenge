package com.ulisescervera.uci.core.format

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ulisescervera.uci.R
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns `:domain` models into strings for the screen.
 *
 * This class is the reason `:domain` has no `getDisplayName()`: composing the
 * title needs a localised type label, a locale-aware number format and a word
 * order that changes per language. All three are resources, and resources are a
 * presentation concern.
 *
 * Every string comes from `strings.xml` with positional placeholders. Nothing
 * is concatenated with `+`, because "Piso - Castellana - Madrid" is not a
 * universal word order and a `%1$s - %2$s - %3$s` format lets a translator
 * reorder it.
 *
 * One instance is shared: [NumberFormat] construction is not free and this is
 * called once per bound row.
 */
@Singleton
class PropertyFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val resources get() = context.resources

    /**
     * Locale-aware, no decimals. Prices in this catalogue are six or seven
     * figures; cents are noise and would push the label out of the card.
     */
    private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance().apply {
        isGroupingUsed = true
    }

    private val oneDecimalFormat: NumberFormat = NumberFormat.getNumberInstance().apply {
        isGroupingUsed = true
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }

    // ---------------------------------------------------------------------
    // Title
    // ---------------------------------------------------------------------

    /**
     * `"{type} - {neighbourhood or district} - {city}"`.
     *
     * Degrades gracefully: with two parts it uses the two-part format, with one
     * it returns it bare. Printing "Piso -  - Madrid" for a property with no
     * neighbourhood would look like a bug, because it is one.
     */
    fun title(property: Property): String = title(property.propertyType, property.address)

    fun title(type: PropertyType, address: PropertyAddress): String {
        val parts = listOfNotNull(
            propertyTypeLabel(type),
            address.zone,
            address.city,
        ).filter(String::isNotBlank)

        return when (parts.size) {
            0 -> propertyTypeLabel(PropertyType.UNKNOWN)
            1 -> parts[0]
            2 -> context.getString(R.string.uci_property_title_two_parts, parts[0], parts[1])
            else -> context.getString(R.string.uci_property_title_full, parts[0], parts[1], parts[2])
        }
    }

    /** Same shape as [title] but for the narrow discarded-sheet projection. */
    fun title(type: PropertyType, zone: String?, city: String?): String =
        title(type, PropertyAddress(neighborhood = zone, municipality = city))

    fun propertyTypeLabel(type: PropertyType): String = context.getString(type.labelRes())

    // ---------------------------------------------------------------------
    // Prices
    // ---------------------------------------------------------------------

    /** `"1.195.000 €"` / `"1.200 €/mes"` -- the suffix comes from the backend. */
    fun price(money: Money): String = when {
        money.amount <= 0.0 -> context.getString(R.string.uci_price_unavailable)
        else -> context.getString(
            R.string.uci_price_format,
            integerFormat.format(money.amount),
            money.currencySuffix,
        )
    }

    /**
     * `"8.985 €/m²"` for a sale, `"5 €/m²/mes"` for a rental.
     *
     * The unit is built from the operation rather than by splicing the API's
     * suffix: naively appending "/m²" to "€/mes" yields "€/mes/m²", which reads
     * as a monthly price per square metre per month.
     *
     * Returns `null` when the surface is unknown, so the caller hides the label
     * instead of showing a zero.
     */
    fun pricePerSquareMeter(property: Property): String? {
        val unit = property.pricePerSquareMeter ?: return null
        if (unit.amount <= 0.0) return null
        val template = when (property.operation) {
            Operation.RENT -> R.string.uci_price_per_square_meter_rent
            Operation.SALE, Operation.UNKNOWN -> R.string.uci_price_per_square_meter_sale
        }
        return context.getString(template, integerFormat.format(unit.amount))
    }

    fun size(squareMeters: Double): String =
        context.getString(R.string.uci_fact_size, oneDecimalFormat.format(squareMeters))

    // ---------------------------------------------------------------------
    // Facts
    // ---------------------------------------------------------------------

    /**
     * One row of icon + label per fact the backend actually reported.
     *
     * The list is built, not templated, so a property with no bathroom count
     * simply has one fewer chip -- rather than a chip reading "0 baños", which
     * a user would read as "this flat has no bathroom".
     */
    fun facts(property: Property): List<Fact> = buildList {
        if (property.sizeSquareMeters > 0.0) {
            add(Fact(R.drawable.ic_uci_ruler, size(property.sizeSquareMeters)))
        }
        if (property.rooms > 0) {
            add(Fact(R.drawable.ic_uci_bed, resources.getQuantityString(R.plurals.uci_fact_rooms, property.rooms, property.rooms)))
        }
        if (property.bathrooms > 0) {
            add(
                Fact(
                    R.drawable.ic_uci_bath,
                    resources.getQuantityString(R.plurals.uci_fact_bathrooms, property.bathrooms, property.bathrooms),
                ),
            )
        }
        // Floor and lift only exist for vertical housing -- a chalet has neither.
        if (property.showsFloorInformation) {
            floorLabel(property.floor)?.let { add(Fact(R.drawable.ic_uci_stairs, it)) }
            liftLabel(property.hasLift)?.let { add(Fact(R.drawable.ic_uci_lift, it)) }
        }
        garageLabel(property.parkingSpace)?.let { add(Fact(R.drawable.ic_uci_garage, it)) }
    }

    /** Single-line version, used for accessibility and for the related carousel. */
    fun factsLine(property: Property): String =
        facts(property).joinToString(separator = FACT_SEPARATOR) { it.label }

    fun floorLabel(floor: Floor): String? = when (floor) {
        is Floor.Numbered -> context.getString(R.string.uci_fact_floor_numbered, floor.value)
        Floor.Ground -> context.getString(R.string.uci_fact_floor_ground)
        Floor.Basement -> context.getString(R.string.uci_fact_floor_basement)
        Floor.Mezzanine -> context.getString(R.string.uci_fact_floor_mezzanine)
        is Floor.Unknown -> context.getString(R.string.uci_fact_floor_raw, floor.raw)
        // Both mean "do not show a floor row", for different reasons.
        Floor.NotApplicable, Floor.Missing -> null
    }

    /**
     * `null` when the list endpoint has not reported the lift yet, which is not
     * the same as "no lift" -- saying "sin ascensor" there would be a factual
     * claim we cannot back up.
     *
     * There used to be a third label for exactly that case ("Ascensor sin
     * confirmar"), but a chip that only says "we don't know" is not a fact
     * about the property, it is a fact about our own data gap, and the user
     * does not need a chip to learn that. Every other fact in this file is
     * already omitted rather than hedged when we cannot back it up (see
     * [floorLabel], [garageLabel]); the lift is now consistent with them.
     */
    fun liftLabel(hasLift: Boolean?): String? = when (hasLift) {
        true -> context.getString(R.string.uci_fact_lift_yes)
        false -> context.getString(R.string.uci_fact_lift_no)
        null -> null
    }

    /** `null` when the backend did not report parking at all. */
    fun garageLabel(parkingSpace: ParkingSpace): String? = when (parkingSpace) {
        is ParkingSpace.Available -> when {
            parkingSpace.isIncludedInPrice -> context.getString(R.string.uci_fact_garage_included)
            else -> context.getString(R.string.uci_fact_garage_extra)
        }
        ParkingSpace.None -> context.getString(R.string.uci_fact_garage_no)
        ParkingSpace.Unreported -> null
    }

    fun exteriorLabel(isExterior: Boolean?): String = when (isExterior) {
        true -> context.getString(R.string.uci_fact_exterior)
        false -> context.getString(R.string.uci_fact_interior)
        null -> context.getString(R.string.uci_value_unknown)
    }

    // ---------------------------------------------------------------------
    // Accessibility
    // ---------------------------------------------------------------------

    /**
     * One utterance for a whole property card: title, price, facts.
     *
     * Without this, TalkBack stops on nine separate nodes per row and the user
     * has to swipe nine times to get past a single listing.
     */
    fun accessibilityDescription(property: Property): String {
        val priceLine = listOfNotNull(price(property.price), pricePerSquareMeter(property))
            .joinToString(separator = FACT_SEPARATOR)
        return context.getString(
            R.string.uci_a11y_property_card,
            title(property),
            priceLine,
            factsLine(property),
        )
    }

    /**
     * A carousel image label. Prefers the backend's own translation ("Salón")
     * over the raw tag, and falls back to a positional description so the user
     * always knows where they are.
     */
    fun imageAccessibilityDescription(
        position: Int,
        total: Int,
        localizedName: String?,
    ): String {
        val oneBased = position + 1
        val name = localizedName?.takeIf(String::isNotBlank)
        return when (name) {
            null -> context.getString(R.string.uci_a11y_carousel_image_untagged, oneBased, total)
            else -> context.getString(R.string.uci_a11y_carousel_image, oneBased, total, name)
        }
    }

    /** Icon + label pair for the facts row. */
    data class Fact(
        @DrawableRes val iconRes: Int,
        val label: String,
    )

    private companion object {
        const val FACT_SEPARATOR = " · "
    }
}

@StringRes
internal fun PropertyType.labelRes(): Int = when (this) {
    PropertyType.FLAT -> R.string.uci_property_type_flat
    PropertyType.DUPLEX -> R.string.uci_property_type_duplex
    PropertyType.PENTHOUSE -> R.string.uci_property_type_penthouse
    PropertyType.STUDIO -> R.string.uci_property_type_studio
    PropertyType.CHALET -> R.string.uci_property_type_chalet
    PropertyType.COUNTRY_HOUSE -> R.string.uci_property_type_country_house
    PropertyType.HOUSE -> R.string.uci_property_type_house
    PropertyType.ROOM -> R.string.uci_property_type_room
    PropertyType.GARAGE -> R.string.uci_property_type_garage
    PropertyType.OFFICE -> R.string.uci_property_type_office
    PropertyType.PREMISES -> R.string.uci_property_type_premises
    PropertyType.UNKNOWN -> R.string.uci_property_type_unknown
}
