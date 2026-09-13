package com.ulisescervera.uci.data.local

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.data.local.converter.UciTypeConverters
import com.ulisescervera.uci.data.local.entity.ImageRecord
import com.ulisescervera.uci.domain.model.PropertyFlag
import org.junit.Test

class UciTypeConvertersTest {

    private val converters = UciTypeConverters()

    @Test
    fun `images round trip with their order intact`() {
        // Order is the carousel order; losing it would shuffle every gallery.
        val images = listOf(
            ImageRecord("https://a.test", "livingRoom", "Salón", 1),
            ImageRecord("https://b.test", "kitchen", "Cocina", 2),
            ImageRecord("https://c.test", null, null, null),
        )

        val restored = converters.jsonToImages(converters.imagesToJson(images))

        assertThat(restored).isEqualTo(images)
    }

    @Test
    fun `a corrupt images column degrades to empty instead of crashing the list`() {
        assertThat(converters.jsonToImages("{not json")).isEmpty()
        assertThat(converters.jsonToImages("")).isEmpty()
        assertThat(converters.jsonToImages(null)).isEmpty()
    }

    @Test
    fun `flags are stored as explicit literals, never as enum names`() {
        // R8 in full mode may rename enum constants. Persisting `enum.name`
        // would make the database contents depend on the build type.
        assertThat(converters.flagToToken(PropertyFlag.NONE)).isEqualTo("none")
        assertThat(converters.flagToToken(PropertyFlag.FAVOURITE)).isEqualTo("favourite")
        assertThat(converters.flagToToken(PropertyFlag.DISCARDED)).isEqualTo("discarded")
    }

    @Test
    fun `the stored tokens match the literals hardcoded in the dao queries`() {
        assertThat(converters.flagToToken(PropertyFlag.FAVOURITE))
            .isEqualTo(UciTypeConverters.Flags.FAVOURITE)
        assertThat(converters.flagToToken(PropertyFlag.DISCARDED))
            .isEqualTo(UciTypeConverters.Flags.DISCARDED)
    }

    @Test
    fun `flags round trip`() {
        PropertyFlag.entries
            .filterNot { it == PropertyFlag.NONE }
            .forEach { flag ->
                assertThat(converters.tokenToFlag(converters.flagToToken(flag))).isEqualTo(flag)
            }
    }

    @Test
    fun `an unknown or absent token reads as no opinion`() {
        assertThat(converters.tokenToFlag(null)).isEqualTo(PropertyFlag.NONE)
        assertThat(converters.tokenToFlag("archived")).isEqualTo(PropertyFlag.NONE)
    }
}
