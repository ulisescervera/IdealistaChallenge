package com.ulisescervera.uci.domain.model

/**
 * `moreCharacteristics` from the detail endpoint: the long tail of attributes
 * rendered underneath the advertiser's comment.
 *
 * Everything is nullable because the block is sparsely populated, and the UI
 * skips absent rows rather than printing "null" or a misleading "no".
 */
data class PropertyCharacteristics(
    val communityCostsPerMonth: Double? = null,
    val rooms: Int? = null,
    val bathrooms: Int? = null,
    val isExterior: Boolean? = null,
    val housingFurniture: String? = null,
    val agencyIsABank: Boolean? = null,
    val energyCertificationType: String? = null,
    val flatLocation: String? = null,
    val modificationDateEpochMillis: Long? = null,
    val constructedAreaSquareMeters: Double? = null,
    val hasLift: Boolean? = null,
    val hasBoxRoom: Boolean? = null,
    val isDuplex: Boolean? = null,
    val floor: Floor = Floor.Missing,
    val status: String? = null,
) {
    companion object {
        val Empty = PropertyCharacteristics()
    }
}
