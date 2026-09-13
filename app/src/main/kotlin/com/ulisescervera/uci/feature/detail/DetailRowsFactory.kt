package com.ulisescervera.uci.feature.detail

import android.content.Context
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.carousel.buildCarouselPages
import com.ulisescervera.uci.core.format.CharacteristicsFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.format.UciDateFormatter
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.Property
import com.ulisescervera.uci.domain.model.PropertyAddress
import com.ulisescervera.uci.domain.model.PropertyCharacteristics
import com.ulisescervera.uci.domain.model.PropertyDetail
import com.ulisescervera.uci.domain.model.PropertyFlag
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [PropertyDetailUiState] into the list of rows the RecyclerView shows.
 *
 * Keeping this out of the ViewModel is deliberate: it needs `Context` for
 * strings and it is pure, so it belongs to the view layer and can be tested
 * with Robolectric on its own -- "given a state with no detail, the rows are
 * gallery, header, actions, comment-skeleton, characteristics-skeleton" is a
 * one-line assertion.
 *
 * The ordering below is the brief's ordering, and the reason for each position:
 *
 * ```
 * 1 gallery          carousel + counter, map as the last page
 * 2 header           name, price, €/m², size, rooms, floor, lift, garage
 * 3 actions          favourite / discard / share + the favourite date
 * 4 comment          the advertiser's text, right after the actions
 * 5 characteristics  "el resto de propiedades restantes", right after it
 * 6 map              the interactive map
 * 7 related          loaded on reaching the end
 * ```
 *
 * The map sits *after* the characteristics rather than between them and the
 * comment, so that the two sections the brief names consecutively really are
 * consecutive. Nothing depends on the map's position: `focusOnMap` scrolls to
 * `indexOfFirst { it is DetailRow.Map }`.
 */
@Singleton
class DetailRowsFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val propertyFormatter: PropertyFormatter,
    private val characteristicsFormatter: CharacteristicsFormatter,
    private val dateFormatter: UciDateFormatter,
) {

    fun build(state: PropertyDetailUiState): List<DetailRow> {
        // Prefer the detail's own copy of a field, fall back to the cached
        // summary. `summary` is the only source for the garage.
        val summary = state.detail?.summary ?: state.summary ?: return emptyList()
        val detail = state.detail

        return buildList {
            add(galleryRow(state, summary, detail))
            add(headerRow(summary, detail))
            add(actionsRow(summary))

            // Comment and characteristics are the two sections that genuinely
            // need the network, so they are the two that get skeletons.
            when {
                detail != null -> add(DetailRow.Comment(detail.comment, state.isCommentExpanded))
                state.isLoadingDetail -> add(DetailRow.CommentSkeleton)
            }

            when {
                detail != null -> add(DetailRow.Characteristics(characteristicRows(detail)))
                state.isLoadingDetail -> add(DetailRow.CharacteristicsSkeleton)
            }

            mapRow(summary, detail)?.let(::add)

            relatedRow(state.related)?.let(::add)
        }
    }

    /**
     * The detail carousel differs from the list's in two ways: it includes the
     * map as its last page, and it opens on the photo the user tapped so the
     * shared-element transition has somewhere to land.
     */
    private fun galleryRow(
        state: PropertyDetailUiState,
        summary: Property,
        detail: PropertyDetail?,
    ): DetailRow.Gallery {
        val images = detail?.carouselImages ?: summary.carouselImages
        val location = detail?.location ?: summary.location
        val pages = buildCarouselPages(
            images = images,
            location = location,
            includeMapPage = true,
            mapLabel = summary.address.zone,
        )
        return DetailRow.Gallery(
            propertyId = summary.id,
            pages = pages,
            // Clamped: the list may have shown 7 photos while a stale cache has
            // fewer, and setCurrentItem past the end throws.
            initialImageIndex = state.initialImageIndex.coerceIn(0, maxOf(0, images.lastIndex)),
        )
    }

    private fun headerRow(summary: Property, detail: PropertyDetail?): DetailRow.Header {
        // Once the detail arrives it is authoritative for the lift and the
        // floor; the garage never is, so it always comes from the summary.
        val enriched = detail?.let {
            summary.copy(
                hasLift = it.hasLift,
                floor = it.floor,
                propertyType = it.propertyType,
                price = it.price,
                operation = it.operation,
            )
        } ?: summary

        return DetailRow.Header(
            title = propertyFormatter.title(enriched),
            price = propertyFormatter.price(enriched.price),
            pricePerSquareMeter = propertyFormatter.pricePerSquareMeter(enriched),
            facts = propertyFormatter.facts(enriched),
            accessibilityDescription = propertyFormatter.accessibilityDescription(enriched),
        )
    }

    private fun actionsRow(summary: Property): DetailRow.Actions = DetailRow.Actions(
        isFavourite = summary.flag == PropertyFlag.FAVOURITE,
        isDiscarded = summary.flag == PropertyFlag.DISCARDED,
        favouritedOn = summary.favouriteMark?.let { mark ->
            context.getString(R.string.uci_detail_favourited_on, dateFormatter.favouriteMark(mark))
        },
    )

    /** `null` when the property has no usable coordinates: no row, no empty map. */
    private fun mapRow(summary: Property, detail: PropertyDetail?): DetailRow.Map? {
        val location = detail?.location ?: summary.location ?: return null
        val addressLine = summary.address.toSingleLine()
        return DetailRow.Map(
            location = location,
            addressLine = addressLine,
            accessibilityDescription = addressLine
                ?.let { context.getString(R.string.uci_a11y_map, it) }
                ?: context.getString(R.string.uci_a11y_map_generic),
        )
    }

    /**
     * The characteristics table, minus everything the header already showed.
     * Repeating "3 habitaciones" twelve pixels below the chip that says
     * "3 habitaciones" is noise, and the brief explicitly asks for *the
     * remaining* properties.
     */
    private fun characteristicRows(detail: PropertyDetail) = characteristicsFormatter
        .rows(
            characteristics = detail.characteristics.withoutHeaderFields(),
            energyCertification = detail.energyCertification,
        )

    private fun PropertyCharacteristics.withoutHeaderFields() = copy(
        rooms = null,
        bathrooms = null,
        hasLift = null,
        floor = Floor.Missing,
    )

    private fun relatedRow(related: RelatedState): DetailRow? = when (related) {
        RelatedState.Idle -> null
        RelatedState.Loading -> DetailRow.RelatedSkeleton
        is RelatedState.Loaded -> DetailRow.Related(related.items)
        // A failed recommendation is not worth an error message: the block
        // simply shows its empty copy.
        is RelatedState.Failed -> DetailRow.Related(emptyList())
    }
}

/** "calle de Lagasca, Castellana, Madrid" -- blanks dropped, never a dangling comma. */
internal fun PropertyAddress.toSingleLine(): String? = listOfNotNull(
    street?.takeIf(String::isNotBlank),
    zone?.takeIf(String::isNotBlank),
    city?.takeIf(String::isNotBlank),
).takeIf { it.isNotEmpty() }?.joinToString(separator = ", ")
