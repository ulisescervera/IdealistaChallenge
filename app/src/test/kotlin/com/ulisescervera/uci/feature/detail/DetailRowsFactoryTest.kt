package com.ulisescervera.uci.feature.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.core.carousel.CarouselPage
import com.ulisescervera.uci.core.format.CharacteristicsFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.format.UciDateFormatter
import com.ulisescervera.uci.domain.model.FavouriteMark
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.support.AppFixtures
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The row list of the detail screen.
 *
 * Testing the factory rather than the fragment is the payoff of moving row
 * construction out of the view: the whole progressive-loading behaviour becomes
 * a set of assertions about a list, with no RecyclerView, no inflation and no
 * Espresso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class DetailRowsFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val propertyFormatter = PropertyFormatter(context)
    private val factory = DetailRowsFactory(
        context = context,
        propertyFormatter = propertyFormatter,
        characteristicsFormatter = CharacteristicsFormatter(
            context = context,
            propertyFormatter = propertyFormatter,
            dateFormatter = UciDateFormatter(context),
        ),
        dateFormatter = UciDateFormatter(context),
    )

    @Test
    fun `an empty state produces no rows at all`() {
        assertThat(factory.build(PropertyDetailUiState())).isEmpty()
    }

    @Test
    fun `with only the cached summary the network sections are skeletons`() {
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = AppFixtures.property("1"),
                isLoadingDetail = true,
            ),
        )

        // Gallery, header and actions are real from the first frame; the comment
        // and the characteristics are the only sections that need the network.
        assertThat(rows.map { it.id }).containsExactly(
            "gallery",
            "header",
            "actions",
            "comment-skeleton",
            "characteristics-skeleton",
            "map",
        ).inOrder()
    }

    @Test
    fun `the loaded detail replaces the skeletons in place`() {
        val summary = AppFixtures.property("1")
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = summary,
                detail = AppFixtures.detail(summary),
                isLoadingDetail = false,
            ),
        )

        assertThat(rows.map { it.id }).containsExactly(
            "gallery",
            "header",
            "actions",
            "comment",
            "characteristics",
            "map",
        ).inOrder()
    }

    @Test
    fun `the map row is omitted for a property without coordinates`() {
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = AppFixtures.property("1", location = null),
                isLoadingDetail = false,
            ),
        )

        assertThat(rows.none { it is DetailRow.Map }).isTrue()
    }

    @Test
    fun `the gallery includes the map as its last page`() {
        val rows = factory.build(
            PropertyDetailUiState(propertyId = "1", summary = AppFixtures.property("1")),
        )

        val gallery = rows.first { it is DetailRow.Gallery } as DetailRow.Gallery
        assertThat(gallery.pages.last()).isInstanceOf(CarouselPage.Map::class.java)
    }

    @Test
    fun `the initial image index is clamped to the images actually available`() {
        // The list may have shown 7 photos while a stale cache holds 3;
        // setCurrentItem past the end throws.
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = AppFixtures.property("1"),
                initialImageIndex = 99,
            ),
        )

        val gallery = rows.first { it is DetailRow.Gallery } as DetailRow.Gallery
        assertThat(gallery.initialImageIndex).isEqualTo(2)
    }

    @Test
    fun `the header uses the lift from the detail once it is known`() {
        val summary = AppFixtures.property("1", hasLift = null)
        val withoutDetail = factory.build(
            PropertyDetailUiState(propertyId = "1", summary = summary),
        ).first { it is DetailRow.Header } as DetailRow.Header
        val withDetail = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = summary,
                detail = AppFixtures.detail(summary, hasLift = true),
                isLoadingDetail = false,
            ),
        ).first { it is DetailRow.Header } as DetailRow.Header

        assertThat(withoutDetail.facts.map { it.label }).contains("Ascensor sin confirmar")
        assertThat(withDetail.facts.map { it.label }).contains("Con ascensor")
    }

    @Test
    fun `the characteristics table omits what the header already showed`() {
        val summary = AppFixtures.property("1")
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = summary,
                detail = AppFixtures.detail(summary),
                isLoadingDetail = false,
            ),
        )

        val characteristics = rows.first { it is DetailRow.Characteristics } as DetailRow.Characteristics
        val labels = characteristics.rows.map { it.label }

        // The brief asks for "el resto de propiedades restantes": repeating
        // "3 habitaciones" under a chip that says "3 habitaciones" is noise.
        assertThat(labels).doesNotContain("Habitaciones")
        assertThat(labels).doesNotContain("Baños")
        assertThat(labels).doesNotContain("Ascensor")
        assertThat(labels).doesNotContain("Planta")
        // ...but the genuinely new fields are there.
        assertThat(labels).contains("Gastos de comunidad")
        assertThat(labels).contains("Superficie construida")
    }

    @Test
    fun `the favourite date is rendered in the stored zone`() {
        val mark = FavouriteMark.at(
            Instant.parse("2026-03-03T18:12:00Z"),
            ZoneId.of("Europe/Madrid"),
        )
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = AppFixtures.property(
                    "1",
                    flag = PropertyFlag.FAVOURITE,
                    favouriteMark = mark,
                ),
            ),
        )

        val actions = rows.first { it is DetailRow.Actions } as DetailRow.Actions
        assertThat(actions.isFavourite).isTrue()
        assertThat(actions.favouritedOn).isNotNull()
        assertThat(actions.favouritedOn).contains("19:12")
    }

    @Test
    fun `no favourite date for a property that is not a favourite`() {
        val rows = factory.build(
            PropertyDetailUiState(propertyId = "1", summary = AppFixtures.property("1")),
        )

        val actions = rows.first { it is DetailRow.Actions } as DetailRow.Actions
        assertThat(actions.favouritedOn).isNull()
    }

    @Test
    fun `the related block is absent until it is requested`() {
        val rows = factory.build(
            PropertyDetailUiState(propertyId = "1", summary = AppFixtures.property("1")),
        )

        assertThat(rows.none { it.id.startsWith("related") }).isTrue()
    }

    @Test
    fun `the related block shows a skeleton while loading and cards when loaded`() {
        val base = PropertyDetailUiState(propertyId = "1", summary = AppFixtures.property("1"))

        val loading = factory.build(base.copy(related = RelatedState.Loading))
        val loaded = factory.build(
            base.copy(related = RelatedState.Loaded(listOf(AppFixtures.property("2")))),
        )

        assertThat(loading.last()).isEqualTo(DetailRow.RelatedSkeleton)
        assertThat(loaded.last()).isEqualTo(DetailRow.Related(listOf(AppFixtures.property("2"))))
    }

    @Test
    fun `a failed related load shows the empty block, not an error`() {
        val rows = factory.build(
            PropertyDetailUiState(
                propertyId = "1",
                summary = AppFixtures.property("1"),
                related = RelatedState.Failed(com.ulisescervera.uci.domain.common.UciError.Timeout),
            ),
        )

        // A recommendation nobody asked for is not worth an error message.
        assertThat(rows.last()).isEqualTo(DetailRow.Related(emptyList()))
    }

    @Test
    fun `the address line drops blanks instead of leaving dangling commas`() {
        val full = AppFixtures.property("1").address.toSingleLine()
        val partial = AppFixtures.property("1", neighborhood = null, municipality = null)
            .address
            .toSingleLine()

        assertThat(full).isEqualTo("calle de Lagasca, Castellana, Madrid")
        assertThat(partial).isEqualTo("calle de Lagasca, Barrio de Salamanca, Madrid")
    }
}
