package com.ulisescervera.uci.data.mapper

import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.data.local.entity.PropertyEntity
import com.ulisescervera.uci.data.network.dto.ImageDto
import com.ulisescervera.uci.data.network.dto.PropertyListItemDto
import com.ulisescervera.uci.domain.common.UciClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `list.json` -> `properties` rows.
 *
 * Decisions encoded here, all of them driven by the actual payload:
 *
 * - **Which price wins.** `priceInfo.price` is the amount the backend wants
 *   displayed, with its own suffix ("€" for sale, "€/mes" for rent). The root
 *   `price` is a sale reference that, for rentals, is a completely different
 *   number (property 2: root `2750000`, priceInfo `1200 €/mes`). Showing the
 *   root value on a rental would misprice the offer by three orders of
 *   magnitude, so `priceInfo` is authoritative and the root is only a fallback.
 * - **Items without a code are dropped.** The primary key cannot be invented,
 *   and a property we cannot address is a property we cannot open, favourite or
 *   discard. [toEntities] filters them out rather than failing the whole page.
 * - **The thumbnail is folded into the carousel** by `Property.carouselImages`
 *   only when `multimedia.images` is empty, so we never show the same photo
 *   twice.
 */
@Singleton
class PropertyDtoMapper @Inject constructor(
    private val clock: UciClock,
) {

    /** Order is preserved as `order_in_feed`; the backend ranking is meaningful. */
    fun toEntities(dtos: List<PropertyListItemDto>): List<PropertyEntity> {
        val cachedAt = clock.now().toEpochMilli()
        return dtos
            .filter { !it.propertyCode.isNullOrBlank() }
            .mapIndexed { index, dto -> dto.toEntity(orderInFeed = index, cachedAt = cachedAt) }
    }

    private fun PropertyListItemDto.toEntity(orderInFeed: Int, cachedAt: Long) = PropertyEntity(
        propertyCode = requireNotNull(propertyCode),
        orderInFeed = orderInFeed,
        propertyType = propertyType.orEmpty(),
        operation = operation.orEmpty(),
        street = address,
        neighborhood = neighborhood,
        district = district,
        municipality = municipality,
        province = province,
        countryCode = country,
        latitude = latitude,
        longitude = longitude,
        priceAmount = priceInfo?.price?.amount ?: price ?: 0.0,
        priceCurrencySuffix = priceInfo?.price?.currencySuffix ?: DEFAULT_CURRENCY_SUFFIX,
        sizeSquareMeters = size ?: 0.0,
        rooms = rooms ?: 0,
        bathrooms = bathrooms ?: 0,
        rawFloor = floor,
        hasParkingSpace = parkingSpace?.hasParkingSpace,
        isParkingIncludedInPrice = parkingSpace?.isIncludedInPrice,
        isExterior = exterior,
        hasAirConditioning = features?.hasAirConditioning,
        hasBoxRoom = features?.hasBoxRoom,
        hasSwimmingPool = features?.hasSwimmingPool,
        hasTerrace = features?.hasTerrace,
        hasGarden = features?.hasGarden,
        thumbnailUrl = thumbnail,
        images = multimedia?.images.toRecords(),
        summary = description.orEmpty(),
        cachedAtEpochMillis = cachedAt,
    )

    private companion object {
        const val DEFAULT_CURRENCY_SUFFIX = "€"
    }
}

/** Shared by the list and the detail mapper. Entries without a URL are useless. */
internal fun List<ImageDto>?.toRecords(): List<ImageRecord> =
    this.orEmpty().mapNotNull { dto ->
        val url = dto.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ImageRecord(
            url = url,
            tag = dto.tag,
            localizedName = dto.localizedName,
            multimediaId = dto.multimediaId,
        )
    }
