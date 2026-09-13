package com.ulisescervera.uci.feature.detail

import com.ulisescervera.uci.core.carousel.CarouselPage
import com.ulisescervera.uci.core.format.CharacteristicsFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.Property

/**
 * One section of the detail screen.
 *
 * The brief asks for the detail to be a *vertical RecyclerView*, so the screen
 * is a list of rows rather than a `ScrollView` of views. That buys three things
 * beyond compliance:
 *
 * - **Progressive rendering falls out for free.** The gallery, header and
 *   actions rows exist from the first frame; [CommentSkeleton] and
 *   [CharacteristicsSkeleton] are simply *replaced* by [Comment] and
 *   [Characteristics] when the network answers, and `DiffUtil` animates the
 *   swap.
 * - **Scroll-to-map is an index lookup**, not a coordinate calculation:
 *   `rows.indexOfFirst { it is DetailRow.Map }`.
 * - **The map is instantiated once**, when its row is bound, instead of living
 *   in the hierarchy from the start and downloading tiles the user may never
 *   look at.
 *
 * Rows are pre-formatted strings, not domain objects: formatting happens once
 * in [DetailRowsFactory] rather than on every bind, and the adapter stays free
 * of `Context`.
 */
sealed interface DetailRow {

    /** DiffUtil identity. Stable across content changes of the same section. */
    val id: String

    data class Gallery(
        val propertyId: String,
        val pages: List<CarouselPage>,
        val initialImageIndex: Int,
    ) : DetailRow {
        override val id: String get() = "gallery"
    }

    data class Header(
        val title: String,
        val price: String,
        val pricePerSquareMeter: String?,
        val facts: List<PropertyFormatter.Fact>,
        val accessibilityDescription: String,
    ) : DetailRow {
        override val id: String get() = "header"
    }

    data class Actions(
        val isFavourite: Boolean,
        val isDiscarded: Boolean,
        /** "Guardado en favoritos el …", already localised and zoned. */
        val favouritedOn: String?,
    ) : DetailRow {
        override val id: String get() = "actions"
    }

    data class Comment(
        val body: String,
        val isExpanded: Boolean,
    ) : DetailRow {
        override val id: String get() = "comment"
    }

    data object CommentSkeleton : DetailRow {
        override val id: String get() = "comment-skeleton"
    }

    data class Map(
        val location: GeoPoint,
        val addressLine: String?,
        val accessibilityDescription: String,
    ) : DetailRow {
        override val id: String get() = "map"
    }

    data class Characteristics(
        val rows: List<CharacteristicsFormatter.Row>,
    ) : DetailRow {
        override val id: String get() = "characteristics"
    }

    data object CharacteristicsSkeleton : DetailRow {
        override val id: String get() = "characteristics-skeleton"
    }

    /**
     * The related block. Carries the domain objects rather than formatted text
     * because its nested adapter formats each card with the same
     * [PropertyFormatter] the main list uses -- which is how the two cannot
     * drift apart.
     */
    data class Related(val items: List<Property>) : DetailRow {
        override val id: String get() = "related"
    }

    data object RelatedSkeleton : DetailRow {
        override val id: String get() = "related-skeleton"
    }
}
