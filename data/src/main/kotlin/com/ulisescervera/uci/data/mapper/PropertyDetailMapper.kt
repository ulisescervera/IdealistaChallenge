package com.ulisescervera.uci.data.mapper

import com.ulisescervera.uci.data.local.entity.PropertyDetailEntity
import com.ulisescervera.uci.data.network.dto.PropertyDetailDto
import com.ulisescervera.uci.domain.common.UciClock
import com.ulisescervera.uci.domain.model.EnergyCertification
import com.ulisescervera.uci.domain.model.EnergyRating
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyCharacteristics
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `detail.json` <-> `property_details` <-> [PropertyDetail].
 *
 * ### The id problem
 * `detail.json` carries no `propertyCode`, only `adid: 1`. It is a static
 * fixture: whichever property you open, the same body comes back. Rather than
 * pretend otherwise, [toEntity] takes the id that was *requested* and stores the
 * response under it. The consequence is documented in README > Limitaciones:
 * every property shows property 1's comment and characteristics. The wiring is
 * correct; the fixture is not parameterised.
 *
 * ### The type problem
 * `propertyType` is the family ("homes"). The housing type is
 * `extendedPropertyType` ("flat") with `homeType` as fallback. Reading the wrong
 * field turns every flat into `PropertyType.UNKNOWN` and silently hides the
 * floor and lift rows, since `Floor.from` returns `NotApplicable` for
 * non-vertical housing.
 */
@Singleton
class PropertyDetailMapper @Inject constructor(
    private val clock: UciClock,
) {

    fun toEntity(propertyId: String, dto: PropertyDetailDto): PropertyDetailEntity {
        val characteristics = dto.moreCharacteristics
        return PropertyDetailEntity(
            propertyCode = propertyId,
            comment = dto.propertyComment.orEmpty(),
            images = dto.multimedia?.images.toRecords(),
            latitude = dto.ubication?.latitude,
            longitude = dto.ubication?.longitude,
            priceAmount = dto.priceInfo?.amount ?: dto.price,
            priceCurrencySuffix = dto.priceInfo?.currencySuffix,
            operation = dto.operation,
            propertyType = dto.housingTypeApiValue(),
            communityCostsPerMonth = characteristics?.communityCosts,
            rooms = characteristics?.roomNumber,
            bathrooms = characteristics?.bathNumber,
            isExterior = characteristics?.exterior,
            housingFurniture = characteristics?.housingFurnitures,
            agencyIsABank = characteristics?.agencyIsABank,
            energyCertificationType = characteristics?.energyCertificationType,
            flatLocation = characteristics?.flatLocation,
            modificationDateEpochMillis = characteristics?.modificationDate,
            constructedAreaSquareMeters = characteristics?.constructedArea,
            hasLift = characteristics?.lift,
            hasBoxRoom = characteristics?.boxroom,
            isDuplex = characteristics?.isDuplex,
            rawFloor = characteristics?.floor,
            status = characteristics?.status,
            energyTitle = dto.energyCertificationDto?.title,
            energyConsumptionType = dto.energyCertificationDto?.energyConsumption?.type,
            energyEmissionsType = dto.energyCertificationDto?.emissions?.type,
            cachedAtEpochMillis = clock.now().toEpochMilli(),
        )
    }

    /**
     * Joins the cached detail with the cached summary.
     *
     * [summary] is never optional: the detail screen is always reached from a
     * property that is already in the cache (list tap, favourite tap or deep
     * link, and the deep link refreshes the list first). Fields the detail
     * endpoint omits -- notably the garage -- fall back to it.
     */
    fun toDomain(entity: PropertyDetailEntity, summary: Property): PropertyDetail {
        val type = PropertyType.from(entity.propertyType).takeIf { it != PropertyType.UNKNOWN }
            ?: summary.propertyType
        return PropertyDetail(
            id = entity.propertyCode,
            summary = summary,
            comment = entity.comment,
            images = entity.images.toDomainImages(),
            location = GeoPoint.orNull(entity.latitude, entity.longitude) ?: summary.location,
            characteristics = PropertyCharacteristics(
                communityCostsPerMonth = entity.communityCostsPerMonth,
                rooms = entity.rooms,
                bathrooms = entity.bathrooms,
                isExterior = entity.isExterior,
                housingFurniture = entity.housingFurniture,
                agencyIsABank = entity.agencyIsABank,
                energyCertificationType = entity.energyCertificationType,
                flatLocation = entity.flatLocation,
                modificationDateEpochMillis = entity.modificationDateEpochMillis,
                constructedAreaSquareMeters = entity.constructedAreaSquareMeters,
                hasLift = entity.hasLift,
                hasBoxRoom = entity.hasBoxRoom,
                isDuplex = entity.isDuplex,
                floor = Floor.from(entity.rawFloor, type),
                status = entity.status,
            ),
            energyCertification = entity.toEnergyCertification(),
            operation = Operation.from(entity.operation).takeIf { it != Operation.UNKNOWN } ?: summary.operation,
            price = Money(
                amount = entity.priceAmount ?: summary.price.amount,
                currencySuffix = entity.priceCurrencySuffix ?: summary.price.currencySuffix,
            ),
            propertyType = type,
        )
    }

    private fun PropertyDetailDto.housingTypeApiValue(): String? =
        extendedPropertyType?.takeIf(String::isNotBlank)
            ?: homeType?.takeIf(String::isNotBlank)
            ?: propertyType

    /** Absent when the advertiser did not provide a certificate at all. */
    private fun PropertyDetailEntity.toEnergyCertification(): EnergyCertification? {
        val consumption = EnergyRating.from(energyConsumptionType)
        val emissions = EnergyRating.from(energyEmissionsType)
        val hasAnything = energyTitle != null ||
            consumption != EnergyRating.UNKNOWN ||
            emissions != EnergyRating.UNKNOWN
        return if (hasAnything) {
            EnergyCertification(title = energyTitle, consumption = consumption, emissions = emissions)
        } else {
            null
        }
    }
}
