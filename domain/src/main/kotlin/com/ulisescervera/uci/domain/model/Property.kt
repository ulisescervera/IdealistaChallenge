package com.ulisescervera.uci.domain.model

/**
 * The property as the list and the related-items carousel need it.
 *
 * This is the model, not the DTO: [flag] and [favouriteMark] are joined in from
 * Room because the API knows nothing about them, and [pricePerSquareMeter] is
 * computed instead of stored so it can never disagree with [price].
 */
data class Property(
    val id: String,
    val propertyType: PropertyType,
    val operation: Operation,
    val address: PropertyAddress,
    val location: GeoPoint?,
    /** Headline amount, taken from `priceInfo` -- "€" for sale, "€/mes" for rent. */
    val price: Money,
    val sizeSquareMeters: Double,
    val rooms: Int,
    val bathrooms: Int,
    val floor: Floor,
    /**
     * `null` while unknown. The list endpoint does not report the lift, only the
     * detail endpoint does, so a property shows "sin datos" until its detail has
     * been fetched once and cached. See README > Limitaciones.
     */
    val hasLift: Boolean?,
    val parkingSpace: ParkingSpace,
    val isExterior: Boolean?,
    val features: PropertyFeatures,
    val thumbnailUrl: String?,
    val images: List<PropertyImage>,
    val summary: String,
    val flag: PropertyFlag = PropertyFlag.NONE,
    val favouriteMark: FavouriteMark? = null,
) {
    /** Drives the "open the detail straight on the map" button in the list. */
    val hasLocation: Boolean get() = location != null

    val pricePerSquareMeter: Money? get() = price.perSquareMeter(sizeSquareMeters)

    /** Floor and lift are only rendered when the housing type has storeys. */
    val showsFloorInformation: Boolean get() = propertyType.isVerticalHousing

    val isFavourite: Boolean get() = flag.isFavourite

    val isDiscarded: Boolean get() = flag.isDiscarded

    /** Carousel content, falling back to the thumbnail when `multimedia` is empty. */
    val carouselImages: List<PropertyImage>
        get() = images.ifEmpty {
            thumbnailUrl?.let { listOf(PropertyImage(url = it, tag = null, localizedName = null, multimediaId = null)) }
                ?: emptyList()
        }

    fun withFlag(newFlag: PropertyFlag, mark: FavouriteMark?): Property = copy(
        flag = newFlag,
        favouriteMark = if (newFlag.isFavourite) mark else null,
    )
}
