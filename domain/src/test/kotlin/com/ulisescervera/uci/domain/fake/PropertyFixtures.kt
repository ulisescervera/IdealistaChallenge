package com.ulisescervera.uci.domain.fake

import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.Operation
import com.ulisescervera.uci.domain.model.ParkingSpace
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyFeatures
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.model.PropertyImage

/**
 * Test data builders.
 *
 * A function with defaults rather than a handful of `val PROPERTY_1` constants:
 * a test that cares about the floor should say so in one argument and inherit
 * everything else, and shared mutable-looking constants invite one test to
 * depend on a field another test changed.
 *
 * The defaults mirror property 1 of the real `list.json`, so a test that passes
 * here is testing against data the app actually receives.
 */
object PropertyFixtures {

    fun property(
        id: String = "1",
        type: com.ulisescervera.uci.domain.model.PropertyType =
            com.ulisescervera.uci.domain.model.PropertyType.FLAT,
        operation: Operation = Operation.SALE,
        priceAmount: Double = 1_195_000.0,
        currencySuffix: String = "€",
        size: Double = 133.0,
        rooms: Int = 3,
        bathrooms: Int = 2,
        floor: Floor = Floor.Numbered(2),
        hasLift: Boolean? = null,
        parkingSpace: ParkingSpace = ParkingSpace.Unreported,
        location: GeoPoint? = GeoPoint(40.4362687, -3.6833686),
        neighborhood: String? = "Castellana",
        district: String? = "Barrio de Salamanca",
        municipality: String? = "Madrid",
        images: List<PropertyImage> = listOf(image("a"), image("b")),
        flag: PropertyFlag = PropertyFlag.NONE,
        favouriteMark: com.ulisescervera.uci.domain.model.FavouriteMark? = null,
    ) = Property(
        id = id,
        propertyType = type,
        operation = operation,
        address = PropertyAddress(
            street = "calle de Lagasca",
            neighborhood = neighborhood,
            district = district,
            municipality = municipality,
            province = "Madrid",
            countryCode = "es",
        ),
        location = location,
        price = Money(priceAmount, currencySuffix),
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

    fun discarded(id: String = "1", discardedAt: Long = 1_000L) = DiscardedProperty(
        id = id,
        propertyType = com.ulisescervera.uci.domain.model.PropertyType.FLAT,
        zone = "Castellana",
        city = "Madrid",
        price = Money(1_195_000.0, "€"),
        thumbnailUrl = null,
        discardedAtEpochMillis = discardedAt,
    )
}
