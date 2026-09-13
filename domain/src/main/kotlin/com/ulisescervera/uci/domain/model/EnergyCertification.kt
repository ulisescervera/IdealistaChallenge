package com.ulisescervera.uci.domain.model

/**
 * Energy certificate. Both scales use the same A..G letters, so they share
 * [EnergyRating] and the UI can colour them with one lookup.
 */
data class EnergyCertification(
    val title: String?,
    val consumption: EnergyRating,
    val emissions: EnergyRating,
)

enum class EnergyRating(val letter: String) {
    A("a"), B("b"), C("c"), D("d"), E("e"), F("f"), G("g"), UNKNOWN("");

    companion object {
        fun from(raw: String?): EnergyRating =
            entries.firstOrNull { it.letter.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}
