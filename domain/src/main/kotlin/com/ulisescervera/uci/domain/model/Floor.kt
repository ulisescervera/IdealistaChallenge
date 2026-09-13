package com.ulisescervera.uci.domain.model

/**
 * The API models the floor as a string because it is not always a number
 * ("bj" = bajo, "ss" = sótano, "en" = entreplanta). A sealed hierarchy keeps
 * that reality without leaking raw strings into the UI, and forces the
 * presentation layer to decide how to render each case.
 */
sealed interface Floor {

    data class Numbered(val value: Int) : Floor

    /** "bj" -- ground floor. */
    data object Ground : Floor

    /** "ss" / "st" -- basement. */
    data object Basement : Floor

    /** "en" -- mezzanine. */
    data object Mezzanine : Floor

    /** The type of housing has no floor at all (chalet, country house...). */
    data object NotApplicable : Floor

    /** Present in the payload but unrecognised; kept so we can log and show it raw. */
    data class Unknown(val raw: String) : Floor

    /** Absent from the payload. */
    data object Missing : Floor

    companion object {
        fun from(raw: String?, propertyType: PropertyType): Floor = when {
            !propertyType.isVerticalHousing -> NotApplicable
            raw.isNullOrBlank() -> Missing
            else -> when (raw.trim().lowercase()) {
                "bj", "0" -> Ground
                "ss", "st", "-1" -> Basement
                "en" -> Mezzanine
                else -> raw.trim().toIntOrNull()?.let(::Numbered) ?: Unknown(raw.trim())
            }
        }
    }
}
