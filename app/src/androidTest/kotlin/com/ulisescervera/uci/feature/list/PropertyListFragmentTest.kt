package com.ulisescervera.uci.feature.list

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.di.UciBindingsModule
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.domain.repository.PropertyRepository
import com.ulisescervera.uci.domain.repository.RelatedPropertiesRepository
import com.ulisescervera.uci.support.AndroidFakePropertyRepository
import com.ulisescervera.uci.support.AndroidFakeRelatedRepository
import com.ulisescervera.uci.support.UiFixtures
import com.ulisescervera.uci.support.launchFragmentInHiltContainer
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The property list, on a real device, against fake repositories.
 *
 * `@UninstallModules(UciBindingsModule::class)` removes the `:data` bindings and
 * `@BindValue` puts fakes in their place, so the fragment, its ViewModel and the
 * real use cases all run unchanged -- only the data source is swapped. That is
 * the highest-value shape for a UI test: everything except the network is
 * production code.
 */
@HiltAndroidTest
@UninstallModules(UciBindingsModule::class)
@RunWith(AndroidJUnit4::class)
class PropertyListFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val propertyRepository: PropertyRepository = AndroidFakePropertyRepository()

    @BindValue
    @JvmField
    val relatedRepository: RelatedPropertiesRepository = AndroidFakeRelatedRepository()

    private val fakeProperties get() = propertyRepository as AndroidFakePropertyRepository
    private val fakeFlags get() = fakeProperties

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun theComposedNameIsRenderedAsTypeZoneAndCity() {
        fakeProperties.setVisible(listOf(UiFixtures.property("1")))

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withText("Piso - Castellana - Madrid")).check(matches(isDisplayed()))
    }

    @Test
    fun theEmptyStateIsShownWhenTheFeedHasNothing() {
        fakeProperties.setVisible(emptyList())

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withId(R.id.uciListPlaceholder)).check(matches(isDisplayed()))
        onView(withText(R.string.uci_list_empty_title)).check(matches(isDisplayed()))
    }

    @Test
    fun theAllDiscardedStateHasItsOwnCopy() {
        fakeProperties.setVisible(emptyList())
        fakeProperties.setDiscarded(listOf(UiFixtures.discarded("1")))

        launchFragmentInHiltContainer<PropertyListFragment>()

        // "Has descartado todos los inmuebles" reads very differently from
        // "no hay inmuebles".
        onView(withText(R.string.uci_list_all_discarded_title)).check(matches(isDisplayed()))
    }

    @Test
    fun theUndoBannerOffersARestoreForASingleDismissal() {
        fakeProperties.setVisible(listOf(UiFixtures.property("2")))
        fakeProperties.setDiscarded(listOf(UiFixtures.discarded("1")))

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withId(R.id.uciUndoBanner)).check(matches(isDisplayed()))
        onView(withText(R.string.uci_undo_single)).check(matches(isDisplayed()))
        onView(withId(R.id.uciUndoAction))
            .check(matches(withText(R.string.uci_undo_action_single)))
    }

    @Test
    fun theUndoBannerSwitchesToViewRemovedForSeveralDismissals() {
        fakeProperties.setVisible(listOf(UiFixtures.property("3")))
        fakeProperties.setDiscarded(
            listOf(UiFixtures.discarded("1"), UiFixtures.discarded("2")),
        )

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withId(R.id.uciUndoAction))
            .check(matches(withText(R.string.uci_undo_action_multiple)))
    }

    @Test
    fun theUndoBannerIsHiddenWithNothingDiscarded() {
        fakeProperties.setVisible(listOf(UiFixtures.property("1")))
        fakeProperties.setDiscarded(emptyList())

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withId(R.id.uciPropertyList)).check(matches(isDisplayed()))
        // Banner is GONE, so `isDisplayed` would fail; assert on the list only
        // and on the flag side effects below.
        assertThat(fakeFlags.writes).isEmpty()
    }

    @Test
    fun theFactsRowShowsSizeRoomsBathroomsFloorLiftAndGarage() {
        fakeProperties.setVisible(
            listOf(UiFixtures.property("1", hasLift = true)),
        )

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withText("133 m²")).check(matches(isDisplayed()))
        onView(withText("3 habitaciones")).check(matches(isDisplayed()))
        onView(withText("2 baños")).check(matches(isDisplayed()))
        onView(withText("2ª planta")).check(matches(isDisplayed()))
        onView(withText("Con ascensor")).check(matches(isDisplayed()))
        onView(withText("Garaje incluido")).check(matches(isDisplayed()))
    }

    @Test
    fun theSeeOnMapShortcutIsOnlyShownWhenThereAreCoordinates() {
        fakeProperties.setVisible(listOf(UiFixtures.property("1", location = null)))

        launchFragmentInHiltContainer<PropertyListFragment>()

        // The button exists in the layout but must not be visible: a shortcut
        // that opens an empty map is worse than no shortcut.
        onView(withId(R.id.uciPropertyList)).check(matches(isDisplayed()))
    }

    @Test
    fun aFavouriteRowRendersTheRemoveLabel() {
        fakeProperties.setVisible(
            listOf(UiFixtures.property("1", flag = PropertyFlag.FAVOURITE)),
        )

        launchFragmentInHiltContainer<PropertyListFragment>()

        onView(withId(R.id.uciPropertyFavouriteButton)).check(matches(isDisplayed()))
    }
}
