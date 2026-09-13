package com.ulisescervera.uci.domain.model

/**
 * Everything the detail screen renders.
 *
 * [summary] is the *same* [Property] the list showed. That is deliberate: the
 * detail screen paints its header from the cached summary immediately (carousel,
 * title, price, size, rooms, floor, lift, garage) and only then fills in
 * [comment], [characteristics] and [energyCertification] once the network
 * answers -- which is exactly the loading behaviour the brief asks for.
 *
 * Note the garage: the detail endpoint never reports it, so [Property.parkingSpace]
 * from the cached summary is the only source of truth for it.
 */
data class PropertyDetail(
    val id: String,
    val summary: Property,
    val comment: String,
    val images: List<PropertyImage>,
    val location: GeoPoint?,
    val characteristics: PropertyCharacteristics,
    val energyCertification: EnergyCertification?,
    val operation: Operation,
    val price: Money,
    val propertyType: PropertyType,
) {
    val flag: PropertyFlag get() = summary.flag
    val favouriteMark: FavouriteMark? get() = summary.favouriteMark
    val hasLocation: Boolean get() = location != null

    /** The detail endpoint is authoritative for the lift; the list is not. */
    val hasLift: Boolean? get() = characteristics.hasLift ?: summary.hasLift

    val floor: Floor
        get() = characteristics.floor.takeIf { it != Floor.Missing } ?: summary.floor

    /** Carousel of the detail, falling back to whatever the list already had. */
    val carouselImages: List<PropertyImage>
        get() = images.ifEmpty { summary.carouselImages }
}
