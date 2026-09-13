package com.ulisescervera.uci.domain.model

/**
 * Housing type. The API sends a free-form string, so [from] never throws: an
 * unrecognised value degrades to [UNKNOWN] instead of dropping the property.
 *
 * [isVerticalHousing] is the domain rule behind "if it is a flat, show the
 * floor and whether it has a lift" -- a chalet has neither.
 */
enum class PropertyType(val apiValue: String) {
    FLAT("flat"),
    DUPLEX("duplex"),
    PENTHOUSE("penthouse"),
    STUDIO("studio"),
    CHALET("chalet"),
    COUNTRY_HOUSE("countryHouse"),
    HOUSE("house"),
    ROOM("room"),
    GARAGE("garage"),
    OFFICE("office"),
    PREMISES("premises"),
    UNKNOWN("");

    /** Floor number and lift only make sense inside a multi-storey building. */
    val isVerticalHousing: Boolean
        get() = this == FLAT || this == DUPLEX || this == PENTHOUSE || this == STUDIO

    companion object {
        fun from(apiValue: String?): PropertyType =
            entries.firstOrNull { it.apiValue.equals(apiValue, ignoreCase = true) } ?: UNKNOWN
    }
}
