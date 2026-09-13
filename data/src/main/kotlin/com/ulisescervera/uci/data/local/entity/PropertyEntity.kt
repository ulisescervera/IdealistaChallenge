package com.ulisescervera.uci.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached `list.json` row.
 *
 * Note what is *absent*: there is no `hasLift` column and no favourite/discarded
 * column.
 *
 * - The lift is only reported by `detail.json`, so it lives in
 *   [PropertyDetailEntity]. Keeping a copy here would be clobbered on every
 *   list refresh.
 * - Flags live in [PropertyFlagEntity] so that refreshing the cache -- a
 *   replaceable mirror of a remote resource -- can never destroy user data.
 *
 * [orderInFeed] preserves the order the backend chose, which is meaningful
 * (relevance ranking) and would otherwise be lost by SQLite.
 */
@Entity(tableName = PropertyEntity.TABLE)
data class PropertyEntity(
    @PrimaryKey
    @ColumnInfo(name = "property_code") val propertyCode: String,
    @ColumnInfo(name = "order_in_feed") val orderInFeed: Int,
    @ColumnInfo(name = "property_type") val propertyType: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "street") val street: String?,
    @ColumnInfo(name = "neighborhood") val neighborhood: String?,
    @ColumnInfo(name = "district") val district: String?,
    @ColumnInfo(name = "municipality") val municipality: String?,
    @ColumnInfo(name = "province") val province: String?,
    @ColumnInfo(name = "country_code") val countryCode: String?,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "price_amount") val priceAmount: Double,
    @ColumnInfo(name = "price_currency_suffix") val priceCurrencySuffix: String,
    @ColumnInfo(name = "size_square_meters") val sizeSquareMeters: Double,
    @ColumnInfo(name = "rooms") val rooms: Int,
    @ColumnInfo(name = "bathrooms") val bathrooms: Int,
    /** Raw string from the API ("2", "bj", "ss"); interpreted by `Floor.from`. */
    @ColumnInfo(name = "raw_floor") val rawFloor: String?,
    @ColumnInfo(name = "has_parking_space") val hasParkingSpace: Boolean?,
    @ColumnInfo(name = "parking_included_in_price") val isParkingIncludedInPrice: Boolean?,
    @ColumnInfo(name = "is_exterior") val isExterior: Boolean?,
    @ColumnInfo(name = "has_air_conditioning") val hasAirConditioning: Boolean?,
    @ColumnInfo(name = "has_box_room") val hasBoxRoom: Boolean?,
    @ColumnInfo(name = "has_swimming_pool") val hasSwimmingPool: Boolean?,
    @ColumnInfo(name = "has_terrace") val hasTerrace: Boolean?,
    @ColumnInfo(name = "has_garden") val hasGarden: Boolean?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "images") val images: List<ImageRecord>,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "cached_at") val cachedAtEpochMillis: Long,
) {
    companion object {
        const val TABLE = "properties"
    }
}
