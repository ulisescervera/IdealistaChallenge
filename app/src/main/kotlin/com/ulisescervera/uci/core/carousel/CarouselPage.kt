package com.ulisescervera.uci.core.carousel

import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.PropertyImage

/**
 * A page of the horizontal carousel.
 *
 * The map is a *page*, not a separate section: the brief asks for the location
 * to appear "as the last element" of the carousel, next to the photo counter.
 * Modelling it as a variant means the pager, the indicator and the accessibility
 * announcements all handle it without a special case at the call site.
 */
sealed interface CarouselPage {

    data class Image(val image: PropertyImage) : CarouselPage

    data class Map(val location: GeoPoint, val label: String?) : CarouselPage

    /** Shown when a property has no photos at all. Never focusable. */
    data object Empty : CarouselPage

    /** DiffUtil identity. */
    val stableId: String
        get() = when (this) {
            is Image -> "image:${image.stableId}"
            is Map -> "map:${location.latitude},${location.longitude}"
            Empty -> "empty"
        }
}

/**
 * Builds the pages for a property.
 *
 * [includeMapPage] is false in the list (rows must stay cheap: a `MapView` per
 * row would be absurd) and true in the detail.
 */
fun buildCarouselPages(
    images: List<PropertyImage>,
    location: GeoPoint?,
    includeMapPage: Boolean,
    mapLabel: String? = null,
): List<CarouselPage> = buildList {
    images.forEach { add(CarouselPage.Image(it)) }
    if (isEmpty() && !(includeMapPage && location != null)) add(CarouselPage.Empty)
    if (includeMapPage && location != null) add(CarouselPage.Map(location, mapLabel))
}
