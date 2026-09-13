package com.ulisescervera.uci.domain.model

/**
 * Garage information. Absent from the payload for most properties, hence
 * [Unreported] rather than a `Boolean`.
 */
sealed interface ParkingSpace {

    data object Unreported : ParkingSpace

    data object None : ParkingSpace

    data class Available(val isIncludedInPrice: Boolean) : ParkingSpace

    val isAvailable: Boolean get() = this is Available

    companion object {
        fun from(hasParkingSpace: Boolean?, isIncludedInPrice: Boolean?): ParkingSpace = when (hasParkingSpace) {
            null -> Unreported
            false -> None
            true -> Available(isIncludedInPrice == true)
        }
    }
}
