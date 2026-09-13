package com.ulisescervera.uci.domain.model

/**
 * An amount plus the suffix the backend wants shown next to it ("€", "€/mes").
 *
 * The suffix is data, not presentation: the same endpoint returns "€" for sales
 * and "€/mes" for rentals, and inventing our own would misreport the offer.
 */
data class Money(
    val amount: Double,
    val currencySuffix: String,
) {
    /**
     * Unit price. Returns `null` for a zero/absent surface instead of `Infinity`,
     * which would otherwise reach the UI as "∞ €/m²".
     */
    fun perSquareMeter(sizeSquareMeters: Double): Money? =
        if (sizeSquareMeters > 0.0) copy(amount = amount / sizeSquareMeters) else null

    companion object {
        val Zero = Money(0.0, "€")
    }
}
