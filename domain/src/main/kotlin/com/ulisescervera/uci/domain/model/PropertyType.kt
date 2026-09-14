package com.ulisescervera.uci.domain.model

/**
 * Housing type. The API sends a free-form string, so [from] never throws: an
 * unrecognised value degrades to [UNKNOWN] instead of dropping the property.
 *
 * Only [FLAT] is listed alongside [UNKNOWN] because it is the only
 * `propertyType` value the list endpoint
 * (https://idealista.github.io/android-challenge/list.json) ever sends: all
 * four properties in that fixture are flats. Widening this enum is a
 * one-line change the day the endpoint starts returning something else --
 * [from] already degrades any value it does not recognise instead of
 * throwing, so nothing else has to change to support that.
 *
 * [isVerticalHousing] is the domain rule behind "if it is a flat, show the
 * floor and whether it has a lift".
 */
enum class PropertyType(val apiValue: String) {
    FLAT("flat"),
    UNKNOWN("");

    /** Floor number and lift only make sense inside a multi-storey building. */
    val isVerticalHousing: Boolean
        get() = this == FLAT

    companion object {
        fun from(apiValue: String?): PropertyType =
            entries.firstOrNull { it.apiValue.equals(apiValue, ignoreCase = true) } ?: UNKNOWN
    }
}
