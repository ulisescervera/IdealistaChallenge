package com.ulisescervera.uci.data.support

import com.ulisescervera.uci.data.network.dto.AmountDto
import com.ulisescervera.uci.data.network.dto.EnergyCertificationDto
import com.ulisescervera.uci.data.network.dto.EnergyRatingDto
import com.ulisescervera.uci.data.network.dto.FeaturesDto
import com.ulisescervera.uci.data.network.dto.ImageDto
import com.ulisescervera.uci.data.network.dto.ListPriceInfoDto
import com.ulisescervera.uci.data.network.dto.MoreCharacteristicsDto
import com.ulisescervera.uci.data.network.dto.MultimediaDto
import com.ulisescervera.uci.data.network.dto.ParkingSpaceDto
import com.ulisescervera.uci.data.network.dto.PropertyDetailDto
import com.ulisescervera.uci.data.network.dto.PropertyListItemDto
import com.ulisescervera.uci.data.network.dto.UbicationDto

/**
 * DTOs shaped like the real payloads, including their quirks:
 *
 * - the list nests the amount under `priceInfo.price`, the detail does not;
 * - a rental's root `price` is the *sale* price and disagrees with `priceInfo`;
 * - the detail's `propertyType` is the family ("homes") and the housing type is
 *   in `extendedPropertyType`.
 *
 * A fixture that smoothed those over would make the mapper tests worthless.
 */
object DtoFixtures {

    fun listItem(
        propertyCode: String? = "1",
        propertyType: String? = "flat",
        operation: String? = "sale",
        rootPrice: Double? = 1_195_000.0,
        displayAmount: Double? = 1_195_000.0,
        currencySuffix: String? = "€",
        floor: String? = "2",
        size: Double? = 133.0,
        rooms: Int? = 3,
        bathrooms: Int? = 2,
        latitude: Double? = 40.4362687,
        longitude: Double? = -3.6833686,
        parkingSpace: ParkingSpaceDto? = null,
        features: FeaturesDto? = FeaturesDto(hasAirConditioning = true, hasBoxRoom = false),
        images: List<ImageDto> = listOf(
            ImageDto(url = "https://example.test/1.webp", tag = "livingRoom"),
            ImageDto(url = "https://example.test/2.webp", tag = "bedroom"),
        ),
    ) = PropertyListItemDto(
        propertyCode = propertyCode,
        thumbnail = "https://example.test/thumb.webp",
        floor = floor,
        price = rootPrice,
        priceInfo = ListPriceInfoDto(AmountDto(displayAmount, currencySuffix)),
        propertyType = propertyType,
        operation = operation,
        size = size,
        exterior = false,
        rooms = rooms,
        bathrooms = bathrooms,
        address = "calle de Lagasca",
        province = "Madrid",
        municipality = "Madrid",
        district = "Barrio de Salamanca",
        country = "es",
        neighborhood = "Castellana",
        latitude = latitude,
        longitude = longitude,
        description = "Venta. Piso en exclusiva.",
        multimedia = MultimediaDto(images),
        parkingSpace = parkingSpace,
        features = features,
    )

    /** Property 2 of the real payload: a rental whose two prices disagree. */
    fun rentalListItem() = listItem(
        propertyCode = "2",
        operation = "rent",
        rootPrice = 2_750_000.0,
        displayAmount = 1_200.0,
        currencySuffix = "€/mes",
        floor = "6",
        size = 241.0,
        rooms = 4,
        bathrooms = 4,
        parkingSpace = ParkingSpaceDto(hasParkingSpace = true, isIncludedInPrice = true),
    )

    fun detail(
        adId: Long? = 1,
        family: String? = "homes",
        extendedType: String? = "flat",
        lift: Boolean? = true,
        floor: String? = "2",
        latitude: Double? = 40.4362687,
        longitude: Double? = -3.6833686,
    ) = PropertyDetailDto(
        adId = adId,
        price = 1_195_000.0,
        priceInfo = AmountDto(1_195_000.0, "€"),
        operation = "sale",
        propertyType = family,
        extendedPropertyType = extendedType,
        homeType = "flat",
        state = "active",
        multimedia = MultimediaDto(
            listOf(
                ImageDto(
                    url = "https://example.test/1.webp",
                    tag = "livingRoom",
                    localizedName = "Salón",
                    multimediaId = 1_459_427_188,
                ),
            ),
        ),
        propertyComment = "Venta.Piso EN EXCLUSIVA. Castellana.",
        ubication = UbicationDto(latitude, longitude),
        country = "es",
        moreCharacteristics = MoreCharacteristicsDto(
            communityCosts = 330.0,
            roomNumber = 3,
            bathNumber = 2,
            exterior = false,
            housingFurnitures = "unknown",
            agencyIsABank = false,
            energyCertificationType = "e",
            flatLocation = "internal",
            modificationDate = 1_727_683_968_000,
            constructedArea = 133.0,
            lift = lift,
            boxroom = false,
            isDuplex = false,
            floor = floor,
            status = "renew",
        ),
        energyCertificationDto = EnergyCertificationDto(
            title = "Certificado energético",
            energyConsumption = EnergyRatingDto("e"),
            emissions = EnergyRatingDto("e"),
        ),
    )
}
