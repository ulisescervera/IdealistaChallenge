package com.ulisescervera.uci.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET detail.json`.
 *
 * Two traps worth knowing about:
 *
 * 1. [propertyType] is the *family* ("homes"), not the housing type. The housing
 *    type the UI needs is [extendedPropertyType] ("flat"), with [homeType] as a
 *    fallback. Mapping the wrong one turns every flat into an unknown type.
 * 2. There is no `propertyCode`, only [adId]. The mapper re-associates the
 *    response with the id that was requested -- see `PropertyDetailMapper`.
 */
@Serializable
data class PropertyDetailDto(
    @SerialName("adid") val adId: Long? = null,
    @SerialName("price") val price: Double? = null,
    @SerialName("priceInfo") val priceInfo: AmountDto? = null,
    @SerialName("operation") val operation: String? = null,
    /** Family: "homes", "premises"... NOT the housing type. */
    @SerialName("propertyType") val propertyType: String? = null,
    /** Actual housing type: "flat", "chalet"... */
    @SerialName("extendedPropertyType") val extendedPropertyType: String? = null,
    @SerialName("homeType") val homeType: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("multimedia") val multimedia: MultimediaDto? = null,
    @SerialName("propertyComment") val propertyComment: String? = null,
    @SerialName("ubication") val ubication: UbicationDto? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("moreCharacteristics") val moreCharacteristics: MoreCharacteristicsDto? = null,
    @SerialName("energyCertification") val energyCertificationDto: EnergyCertificationDto? = null,
)

@Serializable
data class MoreCharacteristicsDto(
    @SerialName("communityCosts") val communityCosts: Double? = null,
    @SerialName("roomNumber") val roomNumber: Int? = null,
    @SerialName("bathNumber") val bathNumber: Int? = null,
    @SerialName("exterior") val exterior: Boolean? = null,
    @SerialName("housingFurnitures") val housingFurnitures: String? = null,
    @SerialName("agencyIsABank") val agencyIsABank: Boolean? = null,
    @SerialName("energyCertificationType") val energyCertificationType: String? = null,
    @SerialName("flatLocation") val flatLocation: String? = null,
    @SerialName("modificationDate") val modificationDate: Long? = null,
    @SerialName("constructedArea") val constructedArea: Double? = null,
    /** Only the detail endpoint reports the lift. The list never does. */
    @SerialName("lift") val lift: Boolean? = null,
    @SerialName("boxroom") val boxroom: Boolean? = null,
    @SerialName("isDuplex") val isDuplex: Boolean? = null,
    @SerialName("floor") val floor: String? = null,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class EnergyCertificationDto(
    @SerialName("title") val title: String? = null,
    @SerialName("energyConsumption") val energyConsumption: EnergyRatingDto? = null,
    @SerialName("emissions") val emissions: EnergyRatingDto? = null,
)

@Serializable
data class EnergyRatingDto(
    @SerialName("type") val type: String? = null,
)
