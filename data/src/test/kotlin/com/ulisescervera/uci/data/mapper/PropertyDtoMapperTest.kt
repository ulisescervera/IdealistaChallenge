package com.ulisescervera.uci.data.mapper

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.network.dto.FeaturesDto
import com.ulisescervera.uci.data.network.dto.ImageDto
import com.ulisescervera.uci.data.network.dto.MultimediaDto
import com.ulisescervera.uci.data.support.DtoFixtures
import com.ulisescervera.uci.data.support.FixedUciClock
import org.junit.Test

/**
 * The list mapper. Every test here corresponds to a real quirk of
 * `list.json` -- these are not hypotheticals.
 */
class PropertyDtoMapperTest {

    private val clock = FixedUciClock()
    private val mapper = PropertyDtoMapper(clock)

    @Test
    fun `priceInfo wins over the root price`() {
        // Property 2 of the real payload: root price 2.750.000 (a sale price)
        // next to priceInfo 1.200 €/mes. Showing the root value would misprice
        // the offer by three orders of magnitude.
        val entity = mapper.toEntities(listOf(DtoFixtures.rentalListItem())).single()

        assertThat(entity.priceAmount).isEqualTo(1_200.0)
        assertThat(entity.priceCurrencySuffix).isEqualTo("€/mes")
    }

    @Test
    fun `the root price is used only when priceInfo is absent`() {
        val dto = DtoFixtures.listItem(displayAmount = null, currencySuffix = null)

        val entity = mapper.toEntities(listOf(dto)).single()

        assertThat(entity.priceAmount).isEqualTo(1_195_000.0)
        assertThat(entity.priceCurrencySuffix).isEqualTo("€")
    }

    @Test
    fun `items without a property code are dropped, not defaulted`() {
        // The primary key cannot be invented, and a property we cannot address
        // is one we cannot open, favourite or discard.
        val entities = mapper.toEntities(
            listOf(
                DtoFixtures.listItem(propertyCode = "1"),
                DtoFixtures.listItem(propertyCode = null),
                DtoFixtures.listItem(propertyCode = "  "),
                DtoFixtures.listItem(propertyCode = "3"),
            ),
        )

        assertThat(entities.map { it.propertyCode }).containsExactly("1", "3").inOrder()
    }

    @Test
    fun `backend order is preserved as order_in_feed`() {
        // The order is a relevance ranking; SQLite would otherwise lose it.
        val entities = mapper.toEntities(
            listOf(
                DtoFixtures.listItem(propertyCode = "9"),
                DtoFixtures.listItem(propertyCode = "4"),
                DtoFixtures.listItem(propertyCode = "7"),
            ),
        )

        assertThat(entities.map { it.propertyCode to it.orderInFeed })
            .containsExactly("9" to 0, "4" to 1, "7" to 2)
            .inOrder()
    }

    @Test
    fun `order_in_feed is recomputed after unmappable items are dropped`() {
        val entities = mapper.toEntities(
            listOf(
                DtoFixtures.listItem(propertyCode = null),
                DtoFixtures.listItem(propertyCode = "5"),
            ),
        )

        // No gap at index 0, or the list would start with an empty slot.
        assertThat(entities.single().orderInFeed).isEqualTo(0)
    }

    @Test
    fun `absent parking and absent features stay null instead of becoming false`() {
        // "not reported" and "does not have it" are different claims.
        val entity = mapper.toEntities(
            listOf(DtoFixtures.listItem(parkingSpace = null, features = null)),
        ).single()

        assertThat(entity.hasParkingSpace).isNull()
        assertThat(entity.isParkingIncludedInPrice).isNull()
        assertThat(entity.hasAirConditioning).isNull()
        assertThat(entity.hasSwimmingPool).isNull()
    }

    @Test
    fun `reported features are carried through, including explicit false`() {
        val entity = mapper.toEntities(
            listOf(
                DtoFixtures.listItem(
                    features = FeaturesDto(
                        hasAirConditioning = true,
                        hasBoxRoom = false,
                        hasSwimmingPool = false,
                        hasTerrace = false,
                        hasGarden = false,
                    ),
                ),
            ),
        ).single()

        assertThat(entity.hasAirConditioning).isTrue()
        assertThat(entity.hasBoxRoom).isFalse()
        assertThat(entity.hasGarden).isFalse()
    }

    @Test
    fun `images without a url are skipped`() {
        val dto = DtoFixtures.listItem(
            images = listOf(
                ImageDto(url = "https://example.test/ok.webp"),
                ImageDto(url = null),
                ImageDto(url = "  "),
            ),
        )

        val entity = mapper.toEntities(listOf(dto)).single()

        assertThat(entity.images.map { it.url }).containsExactly("https://example.test/ok.webp")
    }

    @Test
    fun `an empty multimedia block produces no images and the fallback happens later`() {
        val dto = DtoFixtures.listItem().copy(multimedia = MultimediaDto(emptyList()))

        val entity = mapper.toEntities(listOf(dto)).single()

        // The thumbnail fallback lives in Property.carouselImages, not here, so
        // the cache stores exactly what the backend sent.
        assertThat(entity.images).isEmpty()
        assertThat(entity.thumbnailUrl).isNotNull()
    }

    @Test
    fun `every row is stamped with the injected clock`() {
        val entity = mapper.toEntities(listOf(DtoFixtures.listItem())).single()

        assertThat(entity.cachedAtEpochMillis).isEqualTo(clock.now().toEpochMilli())
    }

    @Test
    fun `missing numbers default to zero rather than failing the whole page`() {
        val dto = DtoFixtures.listItem(size = null, rooms = null, bathrooms = null)

        val entity = mapper.toEntities(listOf(dto)).single()

        assertThat(entity.sizeSquareMeters).isEqualTo(0.0)
        assertThat(entity.rooms).isEqualTo(0)
        assertThat(entity.bathrooms).isEqualTo(0)
    }
}
