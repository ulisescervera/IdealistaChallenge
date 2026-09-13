package com.ulisescervera.uci.core.format

import android.content.Context
import com.ulisescervera.uci.R
import com.ulisescervera.uci.domain.model.EnergyCertification
import com.ulisescervera.uci.domain.model.EnergyRating
import com.ulisescervera.uci.domain.model.PropertyCharacteristics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the "Características" table of the detail screen.
 *
 * Rows are produced only for values the backend actually sent. A table full of
 * "Sin datos" is worse than a shorter table: it implies we asked and got a
 * negative answer.
 *
 * The `flatLocation` / `status` values are backend enums in English
 * ("internal", "renew"); they are mapped to localised copy rather than shown
 * raw, with an unrecognised value falling through to its own raw text so a new
 * backend value degrades visibly instead of vanishing.
 */
@Singleton
class CharacteristicsFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val propertyFormatter: PropertyFormatter,
    private val dateFormatter: UciDateFormatter,
) {

    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

    fun rows(
        characteristics: PropertyCharacteristics,
        energyCertification: EnergyCertification?,
    ): List<Row> = buildList {
        characteristics.constructedAreaSquareMeters
            ?.takeIf { it > 0.0 }
            ?.let { add(row(R.string.uci_characteristic_constructed_area, propertyFormatter.size(it))) }

        characteristics.rooms
            ?.takeIf { it > 0 }
            ?.let { add(row(R.string.uci_characteristic_rooms, numberFormat.format(it))) }

        characteristics.bathrooms
            ?.takeIf { it > 0 }
            ?.let { add(row(R.string.uci_characteristic_bathrooms, numberFormat.format(it))) }

        propertyFormatter.floorLabel(characteristics.floor)
            ?.let { add(row(R.string.uci_characteristic_floor, it)) }

        characteristics.hasLift?.let { add(row(R.string.uci_characteristic_lift, yesNo(it))) }
        characteristics.hasBoxRoom?.let { add(row(R.string.uci_characteristic_box_room, yesNo(it))) }
        characteristics.isDuplex?.let { add(row(R.string.uci_characteristic_duplex, yesNo(it))) }

        characteristics.isExterior
            ?.let { add(row(R.string.uci_characteristic_exterior, propertyFormatter.exteriorLabel(it))) }

        characteristics.flatLocation
            ?.let { add(row(R.string.uci_characteristic_flat_location, flatLocationLabel(it))) }

        characteristics.housingFurniture
            ?.let { add(row(R.string.uci_characteristic_furniture, furnitureLabel(it))) }

        characteristics.status
            ?.let { add(row(R.string.uci_characteristic_status, statusLabel(it))) }

        characteristics.communityCostsPerMonth
            ?.takeIf { it > 0.0 }
            ?.let {
                add(
                    row(
                        R.string.uci_characteristic_community_costs,
                        context.getString(
                            R.string.uci_characteristic_community_costs_value,
                            numberFormat.format(it),
                        ),
                    ),
                )
            }

        energyCertification?.let { certification ->
            add(
                row(
                    R.string.uci_characteristic_energy,
                    context.getString(
                        R.string.uci_characteristic_energy_value,
                        ratingLabel(certification.consumption),
                        ratingLabel(certification.emissions),
                    ),
                ),
            )
        }

        characteristics.agencyIsABank
            ?.takeIf { it }
            ?.let {
                add(
                    Row(
                        label = context.getString(R.string.uci_characteristic_agency_bank),
                        value = context.getString(R.string.uci_value_yes),
                    ),
                )
            }

        characteristics.modificationDateEpochMillis
            ?.takeIf { it > 0L }
            ?.let { add(row(R.string.uci_characteristic_modified, dateFormatter.epochMillisAsDate(it))) }
    }

    private fun row(labelRes: Int, value: String) = Row(context.getString(labelRes), value)

    private fun yesNo(value: Boolean): String =
        context.getString(if (value) R.string.uci_value_yes else R.string.uci_value_no)

    private fun ratingLabel(rating: EnergyRating): String = when (rating) {
        EnergyRating.UNKNOWN -> context.getString(R.string.uci_value_unknown)
        else -> rating.letter.uppercase()
    }

    private fun flatLocationLabel(raw: String): String = when (raw.lowercase()) {
        "internal" -> context.getString(R.string.uci_value_flat_location_internal)
        "external" -> context.getString(R.string.uci_value_flat_location_external)
        else -> raw
    }

    private fun statusLabel(raw: String): String = when (raw.lowercase()) {
        "good" -> context.getString(R.string.uci_value_status_good)
        "renew" -> context.getString(R.string.uci_value_status_renew)
        "newdevelopment", "new" -> context.getString(R.string.uci_value_status_new)
        else -> raw
    }

    private fun furnitureLabel(raw: String): String = when (raw.lowercase()) {
        "unknown", "" -> context.getString(R.string.uci_value_furniture_unknown)
        else -> raw
    }

    data class Row(val label: String, val value: String)
}
