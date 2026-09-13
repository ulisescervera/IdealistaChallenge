package com.ulisescervera.uci.domain.model

/** Sale or rent. Drives whether the price is a total or a monthly amount. */
enum class Operation(val apiValue: String) {
    SALE("sale"),
    RENT("rent"),
    UNKNOWN("");

    companion object {
        fun from(apiValue: String?): Operation =
            entries.firstOrNull { it.apiValue.equals(apiValue, ignoreCase = true) } ?: UNKNOWN
    }
}
