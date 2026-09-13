package com.ulisescervera.uci.core.carousel

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.GeoPoint
import com.ulisescervera.uci.domain.model.PropertyImage
import org.junit.Test

class CarouselPagesTest {

    private val images = listOf(image("a"), image("b"), image("c"))
    private val madrid = GeoPoint(40.4362687, -3.6833686)

    @Test
    fun `the list carousel is photos only`() {
        // A MapView per visible row would make scrolling stutter.
        val pages = buildCarouselPages(images, madrid, includeMapPage = false)

        assertThat(pages).hasSize(3)
        assertThat(pages.all { it is CarouselPage.Image }).isTrue()
    }

    @Test
    fun `the detail carousel appends the map as its last page`() {
        val pages = buildCarouselPages(images, madrid, includeMapPage = true)

        assertThat(pages).hasSize(4)
        assertThat(pages.last()).isInstanceOf(CarouselPage.Map::class.java)
    }

    @Test
    fun `no map page without coordinates`() {
        val pages = buildCarouselPages(images, location = null, includeMapPage = true)

        assertThat(pages).hasSize(3)
        assertThat(pages.none { it is CarouselPage.Map }).isTrue()
    }

    @Test
    fun `a property with no photos gets an explicit empty page`() {
        val pages = buildCarouselPages(emptyList(), location = null, includeMapPage = false)

        // The absence is announced rather than shown as a blank rectangle.
        assertThat(pages).containsExactly(CarouselPage.Empty)
    }

    @Test
    fun `a property with no photos but a location shows only the map`() {
        val pages = buildCarouselPages(emptyList(), madrid, includeMapPage = true)

        // No point in an "no photos" placeholder next to a usable map page.
        assertThat(pages).hasSize(1)
        assertThat(pages.single()).isInstanceOf(CarouselPage.Map::class.java)
    }

    @Test
    fun `the image counter counts photos, not pages`() {
        val pages = buildCarouselPages(images, madrid, includeMapPage = true)

        // "3 / 4" on a property with three photos would be wrong.
        assertThat(pages.imageCount()).isEqualTo(3)
    }

    @Test
    fun `page identity is stable so diffing keeps the visible page`() {
        val first = buildCarouselPages(images, madrid, includeMapPage = true)
        val again = buildCarouselPages(images, madrid, includeMapPage = true)

        assertThat(first.map { it.stableId }).isEqualTo(again.map { it.stableId })
    }

    @Test
    fun `isImagePage tells the map page apart`() {
        val pages = buildCarouselPages(images, madrid, includeMapPage = true)

        assertThat(pages.isImagePage(0)).isTrue()
        assertThat(pages.isImagePage(3)).isFalse()
        assertThat(pages.isImagePage(99)).isFalse()
    }

    private fun image(id: String) = PropertyImage(
        url = "https://example.test/$id.webp",
        tag = null,
        localizedName = null,
        multimediaId = null,
    )
}
