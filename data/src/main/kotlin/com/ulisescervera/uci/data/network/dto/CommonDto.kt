package com.ulisescervera.uci.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fragments shared by `list.json` and `detail.json`.
 *
 * Every property is nullable with a default. This is not defensive noise: the
 * challenge payload genuinely omits keys per item (`parkingSpace` exists on
 * property 2 and not on property 1, `features` carries five keys on property 4
 * and two on property 1). A non-null field here would mean a parse failure that
 * drops the whole list.
 */

@Serializable
data class AmountDto(
    @SerialName("amount") val amount: Double? = null,
    @SerialName("currencySuffix") val currencySuffix: String? = null,
)

/**
 * `list.json` nests the amount one level deeper than `detail.json` does:
 *
 * ```
 * list.json    -> "priceInfo": { "price": { "amount": …, "currencySuffix": … } }
 * detail.json  -> "priceInfo": {            "amount": …, "currencySuffix": …   }
 * ```
 *
 * Two DTOs instead of one lenient one, so the difference is documented by the
 * type system rather than by a comment that rots.
 */
@Serializable
data class ListPriceInfoDto(
    @SerialName("price") val price: AmountDto? = null,
)

@Serializable
data class MultimediaDto(
    @SerialName("images") val images: List<ImageDto> = emptyList(),
)

@Serializable
data class ImageDto(
    @SerialName("url") val url: String? = null,
    @SerialName("tag") val tag: String? = null,
    /** Present only in `detail.json`; already translated by the backend. */
    @SerialName("localizedName") val localizedName: String? = null,
    @SerialName("multimediaId") val multimediaId: Long? = null,
)

@Serializable
data class ParkingSpaceDto(
    @SerialName("hasParkingSpace") val hasParkingSpace: Boolean? = null,
    @SerialName("isParkingSpaceIncludedInPrice") val isIncludedInPrice: Boolean? = null,
)

@Serializable
data class FeaturesDto(
    @SerialName("hasAirConditioning") val hasAirConditioning: Boolean? = null,
    @SerialName("hasBoxRoom") val hasBoxRoom: Boolean? = null,
    @SerialName("hasSwimmingPool") val hasSwimmingPool: Boolean? = null,
    @SerialName("hasTerrace") val hasTerrace: Boolean? = null,
    @SerialName("hasGarden") val hasGarden: Boolean? = null,
)

@Serializable
data class UbicationDto(
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
)
