package com.ulisescervera.uci.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached `detail.json` row.
 *
 * Flattened rather than nested: Room can `@Embedded` sub-objects, but a flat row
 * keeps the generated SQL readable and makes every field individually queryable
 * if a filter is ever added.
 *
 * No foreign key to [PropertyEntity] either -- see `PropertyDao.replaceFeed`,
 * which deletes stale properties explicitly and leaves details for ids that are
 * still in the feed untouched.
 */
@Entity(tableName = PropertyDetailEntity.TABLE)
data class PropertyDetailEntity(
    @PrimaryKey
    @ColumnInfo(name = "property_code") val propertyCode: String,
    @ColumnInfo(name = "comment") val comment: String,
    @ColumnInfo(name = "images") val images: List<ImageRecord>,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "price_amount") val priceAmount: Double?,
    @ColumnInfo(name = "price_currency_suffix") val priceCurrencySuffix: String?,
    @ColumnInfo(name = "operation") val operation: String?,
    @ColumnInfo(name = "property_type") val propertyType: String?,
    @ColumnInfo(name = "community_costs") val communityCostsPerMonth: Double?,
    @ColumnInfo(name = "rooms") val rooms: Int?,
    @ColumnInfo(name = "bathrooms") val bathrooms: Int?,
    @ColumnInfo(name = "is_exterior") val isExterior: Boolean?,
    @ColumnInfo(name = "housing_furniture") val housingFurniture: String?,
    @ColumnInfo(name = "agency_is_a_bank") val agencyIsABank: Boolean?,
    @ColumnInfo(name = "energy_certification_type") val energyCertificationType: String?,
    @ColumnInfo(name = "flat_location") val flatLocation: String?,
    @ColumnInfo(name = "modification_date") val modificationDateEpochMillis: Long?,
    @ColumnInfo(name = "constructed_area") val constructedAreaSquareMeters: Double?,
    @ColumnInfo(name = "has_lift") val hasLift: Boolean?,
    @ColumnInfo(name = "has_box_room") val hasBoxRoom: Boolean?,
    @ColumnInfo(name = "is_duplex") val isDuplex: Boolean?,
    @ColumnInfo(name = "raw_floor") val rawFloor: String?,
    @ColumnInfo(name = "status") val status: String?,
    @ColumnInfo(name = "energy_title") val energyTitle: String?,
    @ColumnInfo(name = "energy_consumption_type") val energyConsumptionType: String?,
    @ColumnInfo(name = "energy_emissions_type") val energyEmissionsType: String?,
    @ColumnInfo(name = "cached_at") val cachedAtEpochMillis: Long,
) {
    companion object {
        const val TABLE = "property_details"
    }
}
