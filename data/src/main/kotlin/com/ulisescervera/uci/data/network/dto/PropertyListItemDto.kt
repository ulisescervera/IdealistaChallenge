package com.ulisescervera.uci.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One element of `GET list.json`.
 *
 * Mirrors the wire format one-to-one and nothing else -- no computed values, no
 * enums, no `Instant`. Interpretation happens in the mappers, so a backend
 * rename is a one-line change here instead of a refactor across the app.
 *
 * Field names are explicit via [SerialName] rather than relying on the Kotlin
 * property name, which lets R8 obfuscate this class freely.
 */
@Serializable
data class PropertyListItemDto(
    @SerialName("propertyCode") val propertyCode: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("floor") val floor: String? = null,
    /** Reference amount. For rentals this is the sale price, not the rent. */
    @SerialName("price") val price: Double? = null,
    /** The amount actually shown to the user, with its own suffix. */
    @SerialName("priceInfo") val priceInfo: ListPriceInfoDto? = null,
    @SerialName("propertyType") val propertyType: String? = null,
    @SerialName("operation") val operation: String? = null,
    @SerialName("size") val size: Double? = null,
    @SerialName("exterior") val exterior: Boolean? = null,
    @SerialName("rooms") val rooms: Int? = null,
    @SerialName("bathrooms") val bathrooms: Int? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("province") val province: String? = null,
    @SerialName("municipality") val municipality: String? = null,
    @SerialName("district") val district: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("neighborhood") val neighborhood: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("multimedia") val multimedia: MultimediaDto? = null,
    @SerialName("parkingSpace") val parkingSpace: ParkingSpaceDto? = null,
    @SerialName("features") val features: FeaturesDto? = null,
)
