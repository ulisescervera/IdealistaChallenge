package com.ulisescervera.uci.domain.model

/**
 * Location as text. The composed list title is
 * `"{type} - {neighborhood or district} - {municipality}"`, so the fallback
 * chain lives here (domain rule) while the localised type label and the
 * separator live in `:app` (presentation concern).
 */
data class PropertyAddress(
    val street: String? = null,
    val neighborhood: String? = null,
    val district: String? = null,
    val municipality: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
) {
    /** Neighbourhood if the backend sent one, otherwise the district. */
    val zone: String?
        get() = neighborhood?.takeIf(String::isNotBlank) ?: district?.takeIf(String::isNotBlank)

    /** Municipality if present, otherwise the province -- never blank. */
    val city: String?
        get() = municipality?.takeIf(String::isNotBlank) ?: province?.takeIf(String::isNotBlank)
}
