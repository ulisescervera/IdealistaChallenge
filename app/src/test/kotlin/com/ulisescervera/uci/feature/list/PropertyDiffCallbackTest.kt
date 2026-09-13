package com.ulisescervera.uci.feature.list

import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.FavouriteMark
import com.ulisescervera.uci.domain.model.Money
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.support.AppFixtures
import org.junit.Test

/**
 * The change-payload rule of the property list.
 *
 * This is the subtlest piece of the adapter and it is worth its own file: get it
 * wrong in the permissive direction and toggling a favourite silently stops
 * updating a row; get it wrong in the strict direction and the carousel snaps
 * back to photo 1 every time the user taps the heart.
 *
 * No Robolectric and no views: the rule is pure, so the test is pure.
 */
class PropertyDiffCallbackTest {

    private val mark = FavouriteMark(epochMillis = 1_000L, zoneId = "Europe/Madrid")

    @Test
    fun `identity is the property id, not equality`() {
        val original = AppFixtures.property("1")
        val repriced = AppFixtures.property("1", price = Money(999.0, "€"))

        assertThat(PropertyDiffCallback.areItemsTheSame(original, repriced)).isTrue()
        assertThat(PropertyDiffCallback.areContentsTheSame(original, repriced)).isFalse()
    }

    @Test
    fun `different properties are different items`() {
        assertThat(
            PropertyDiffCallback.areItemsTheSame(
                AppFixtures.property("1"),
                AppFixtures.property("2"),
            ),
        ).isFalse()
    }

    @Test
    fun `a favourite toggle is a partial update`() {
        val before = AppFixtures.property("1")
        val after = AppFixtures.property("1", flag = PropertyFlag.FAVOURITE, favouriteMark = mark)

        // The payload is what keeps the carousel on the photo the user was
        // looking at.
        assertThat(PropertyDiffCallback.getChangePayload(before, after))
            .isEqualTo(PropertyDiffCallback.PAYLOAD_FLAG)
    }

    @Test
    fun `un favouriting is a partial update too`() {
        val before = AppFixtures.property("1", flag = PropertyFlag.FAVOURITE, favouriteMark = mark)
        val after = AppFixtures.property("1")

        assertThat(PropertyDiffCallback.getChangePayload(before, after))
            .isEqualTo(PropertyDiffCallback.PAYLOAD_FLAG)
    }

    @Test
    fun `a change to the favourite timestamp alone is still a partial update`() {
        val before = AppFixtures.property("1", flag = PropertyFlag.FAVOURITE, favouriteMark = mark)
        val after = AppFixtures.property(
            "1",
            flag = PropertyFlag.FAVOURITE,
            favouriteMark = mark.copy(epochMillis = 2_000L),
        )

        assertThat(PropertyDiffCallback.getChangePayload(before, after))
            .isEqualTo(PropertyDiffCallback.PAYLOAD_FLAG)
    }

    @Test
    fun `a data change forces a full rebind, not a partial one`() {
        val before = AppFixtures.property("1")
        val after = AppFixtures.property("1", price = Money(2_000_000.0, "€"))

        // Returning the flag payload here would leave the old price on screen.
        assertThat(PropertyDiffCallback.getChangePayload(before, after)).isNull()
    }

    @Test
    fun `a flag change combined with a data change forces a full rebind`() {
        val before = AppFixtures.property("1")
        val after = AppFixtures.property(
            "1",
            price = Money(2_000_000.0, "€"),
            flag = PropertyFlag.FAVOURITE,
            favouriteMark = mark,
        )

        // This is the case the "normalise then compare" shape exists for: a
        // field-by-field diff would have to remember to check the price.
        assertThat(PropertyDiffCallback.getChangePayload(before, after)).isNull()
    }

    @Test
    fun `identical properties need no payload at all`() {
        assertThat(
            PropertyDiffCallback.getChangePayload(
                AppFixtures.property("1"),
                AppFixtures.property("1"),
            ),
        ).isNull()
    }

    @Test
    fun `the lift arriving from the detail forces a full rebind`() {
        // It changes a visible chip ("Ascensor sin confirmar" -> "Con ascensor"),
        // so a flag-only payload would leave the stale label on screen.
        val before = AppFixtures.property("1", hasLift = null)
        val after = AppFixtures.property("1", hasLift = true)

        assertThat(PropertyDiffCallback.getChangePayload(before, after)).isNull()
    }
}
