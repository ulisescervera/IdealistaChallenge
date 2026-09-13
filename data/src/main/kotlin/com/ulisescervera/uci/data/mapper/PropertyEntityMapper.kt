package com.ulisescervera.uci.data.mapper

import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.pojo.DiscardedPropertyRow
import com.ulisescervera.uci.data.local.pojo.PropertyWithLocalState
import com.ulisescervera.uci.domain.model.DiscardedProperty
import com.ulisescervera.uci.domain.model.FavouriteMark
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
import com.ulisescervera.uci.domain.model.PropertyType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room rows -> `:domain` models. This is where three tables become one object
 * and raw strings become closed types.
 *
 * The interesting part is [toDomain]:
 *
 * - The flag row is absent for most properties, which reads as
 *   [PropertyFlag.NONE] -- "the user has no opinion", not "the user said no".
 * - The favourite mark is only attached when the flag is actually
 *   [PropertyFlag.FAVOURITE], so a stale timestamp from a previous favourite can
 *   never be rendered next to a non-favourite.
 * - `hasLift` comes from the cached *detail*, because the list endpoint never
 *   reports it. Until the detail has been opened once it stays `null`, and the
 *   UI says "sin datos" instead of guessing "no".
 *
 * Overloads are named apart (`toDomain` / `toDomainList` / `toDiscarded…`)
 * rather than relying on `@JvmName`: `List<A>` and `List<B>` erase to the same
 * JVM signature, and a platform clash at this layer is not worth the brevity.
 */
@Singleton
class PropertyEntityMapper @Inject constructor() {

    fun toDomain(row: PropertyWithLocalState): Property {
        val entity = row.property
        val type = PropertyType.from(entity.propertyType)
        val flag = row.flag?.flag ?: PropertyFlag.NONE
        return Property(
            id = entity.propertyCode,
            propertyType = type,
            operation = Operation.from(entity.operation),
            address = PropertyAddress(
                street = entity.street,
                neighborhood = entity.neighborhood,
                district = entity.district,
                municipality = entity.municipality,
                province = entity.province,
                countryCode = entity.countryCode,
            ),
            location = GeoPoint.orNull(entity.latitude, entity.longitude),
            price = Money(entity.priceAmount, entity.priceCurrencySuffix),
            sizeSquareMeters = entity.sizeSquareMeters,
            rooms = entity.rooms,
            bathrooms = entity.bathrooms,
            floor = Floor.from(entity.rawFloor, type),
            hasLift = row.detail?.hasLift,
            parkingSpace = ParkingSpace.from(entity.hasParkingSpace, entity.isParkingIncludedInPrice),
            isExterior = entity.isExterior,
            features = PropertyFeatures(
                hasAirConditioning = entity.hasAirConditioning,
                hasBoxRoom = entity.hasBoxRoom,
                hasSwimmingPool = entity.hasSwimmingPool,
                hasTerrace = entity.hasTerrace,
                hasGarden = entity.hasGarden,
            ),
            thumbnailUrl = entity.thumbnailUrl,
            images = entity.images.toDomainImages(),
            summary = entity.summary,
            flag = flag,
            favouriteMark = row.flag
                ?.takeIf { flag.isFavourite }
                ?.let { FavouriteMark(epochMillis = it.updatedAtEpochMillis, zoneId = it.timeZoneId) },
        )
    }

    fun toDomainList(rows: List<PropertyWithLocalState>): List<Property> = rows.map(::toDomain)

    fun toDiscarded(row: DiscardedPropertyRow): DiscardedProperty = DiscardedProperty(
        id = row.propertyCode,
        propertyType = PropertyType.from(row.propertyType),
        zone = row.neighborhood?.takeIf(String::isNotBlank) ?: row.district?.takeIf(String::isNotBlank),
        city = row.municipality?.takeIf(String::isNotBlank) ?: row.province?.takeIf(String::isNotBlank),
        price = Money(row.priceAmount, row.priceCurrencySuffix),
        thumbnailUrl = row.thumbnailUrl,
        discardedAtEpochMillis = row.discardedAtEpochMillis,
    )

    fun toDiscardedList(rows: List<DiscardedPropertyRow>): List<DiscardedProperty> = rows.map(::toDiscarded)
}

internal fun List<ImageRecord>.toDomainImages(): List<PropertyImage> = map { record ->
    PropertyImage(
        url = record.url,
        tag = record.tag,
        localizedName = record.localizedName,
        multimediaId = record.multimediaId,
    )
}
