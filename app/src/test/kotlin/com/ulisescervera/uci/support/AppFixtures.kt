package com.ulisescervera.uci.support

import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.FavouriteMark
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyCharacteristics
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFeatures
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.model.PropertyImage
import com.ulisescervera.uci.domain.model.PropertyType

/** Builders mirroring property 1 of the real payload. */
object AppFixtures {

    fun property(
        id: String = "1",
        type: PropertyType = PropertyType.FLAT,
        operation: Operation = Operation.SALE,
        price: Money = Money(1_195_000.0, "€"),
        size: Double = 133.0,
        rooms: Int = 3,
        bathrooms: Int = 2,
        floor: Floor = Floor.Numbered(2),
        hasLift: Boolean? = null,
        parkingSpace: ParkingSpace = ParkingSpace.Unreported,
        location: GeoPoint? = GeoPoint(40.4362687, -3.6833686),
        neighborhood: String? = "Castellana",
        municipality: String? = "Madrid",
        images: List<PropertyImage> = listOf(image("a"), image("b"), image("c")),
        flag: PropertyFlag = PropertyFlag.NONE,
        favouriteMark: FavouriteMark? = null,
    ) = Property(
        id = id,
        propertyType = type,
        operation = operation,
        address = PropertyAddress(
            street = "calle de Lagasca",
            neighborhood = neighborhood,
            district = "Barrio de Salamanca",
            municipality = municipality,
            province = "Madrid",
            countryCode = "es",
        ),
        location = location,
        price = price,
        sizeSquareMeters = size,
        rooms = rooms,
        bathrooms = bathrooms,
        floor = floor,
        hasLift = hasLift,
        parkingSpace = parkingSpace,
        isExterior = false,
        features = PropertyFeatures(hasAirConditioning = true, hasBoxRoom = false),
        thumbnailUrl = "https://example.test/thumb.webp",
        images = images,
        summary = "Venta. Piso en exclusiva.",
        flag = flag,
        favouriteMark = favouriteMark,
    )

    fun image(id: String) = PropertyImage(
        url = "https://example.test/$id.webp",
        tag = "livingRoom",
        localizedName = "Salón",
        multimediaId = id.hashCode().toLong(),
    )

    fun detail(
        summary: Property = property(),
        comment: String = "Venta.Piso EN EXCLUSIVA. Castellana.",
        hasLift: Boolean? = true,
        floor: Floor = Floor.Numbered(2),
        location: GeoPoint? = GeoPoint(40.4362687, -3.6833686),
    ) = PropertyDetail(
        id = summary.id,
        summary = summary,
        comment = comment,
        images = summary.images,
        location = location,
        characteristics = PropertyCharacteristics(
            communityCostsPerMonth = 330.0,
            rooms = 3,
            bathrooms = 2,
            isExterior = false,
            housingFurniture = "unknown",
            agencyIsABank = false,
            energyCertificationType = "e",
            flatLocation = "internal",
            modificationDateEpochMillis = 1_727_683_968_000,
            constructedAreaSquareMeters = 133.0,
            hasLift = hasLift,
            hasBoxRoom = false,
            isDuplex = false,
            floor = floor,
            status = "renew",
        ),
        energyCertification = null,
        operation = summary.operation,
        price = summary.price,
        propertyType = summary.propertyType,
    )

    fun discarded(id: String = "1", at: Long = 1_000L) = DiscardedProperty(
        id = id,
        propertyType = PropertyType.FLAT,
        zone = "Castellana",
        city = "Madrid",
        price = Money(1_195_000.0, "€"),
        thumbnailUrl = null,
        discardedAtEpochMillis = at,
    )
}
