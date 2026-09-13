package com.ulisescervera.uci.data.local.pojo

import androidx.room.ColumnInfo

/**
 * Narrow projection for the "discarded items" sheet.
 *
 * Deliberately not a [PropertyWithLocalState]: the sheet needs six fields, and
 * reading the full row would drag every image JSON blob of every dismissed
 * property into memory for a bottom sheet.
 */
data class DiscardedPropertyRow(
    @ColumnInfo(name = "property_code") val propertyCode: String,
    @ColumnInfo(name = "property_type") val propertyType: String,
    @ColumnInfo(name = "neighborhood") val neighborhood: String?,
    @ColumnInfo(name = "district") val district: String?,
    @ColumnInfo(name = "municipality") val municipality: String?,
    @ColumnInfo(name = "province") val province: String?,
    @ColumnInfo(name = "price_amount") val priceAmount: Double,
    @ColumnInfo(name = "price_currency_suffix") val priceCurrencySuffix: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "discarded_at") val discardedAtEpochMillis: Long,
)
