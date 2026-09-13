package com.ulisescervera.uci.feature.favourites

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.core.compose.LocalPropertyFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.theme.UciTheme
import com.ulisescervera.uci.domain.model.PropertyFlag
import com.ulisescervera.uci.support.UiFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Compose favourites screen.
 *
 * No Hilt and no ViewModel: [FavouritesScreen] is stateless, so a test builds
 * the exact state it wants and asserts on what is drawn. Every branch --
 * loading, empty, populated -- is reachable in one line.
 */
@RunWith(AndroidJUnit4::class)
class FavouritesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter =
        PropertyFormatter(ApplicationProvider.getApplicationContext())

    private fun setScreen(
        state: FavouritesUiState,
        onIntent: (FavouritesIntent) -> Unit = {},
    ) {
        composeRule.setContent {
            UciTheme {
                CompositionLocalProvider(LocalPropertyFormatter provides formatter) {
                    FavouritesScreen(state = state, onIntent = onIntent)
                }
            }
        }
    }

    @Test
    fun theEmptyStateExplainsHowToAddAFavourite() {
        setScreen(FavouritesUiState(properties = emptyList(), isLoading = false))

        composeRule.onNodeWithText("Aún no tienes favoritos").assertIsDisplayed()
        composeRule.onNodeWithText("Pulsa el corazón en cualquier inmueble para guardarlo aquí.")
            .assertIsDisplayed()
    }

    @Test
    fun theLoadingStateIsAnnouncedOnceAndNotAsEmpty() {
        setScreen(FavouritesUiState(isLoading = true))

        // The skeleton boxes themselves are semantically invisible; the
        // container carries a single description.
        composeRule.onNodeWithContentDescription("Cargando inmuebles").assertIsDisplayed()
        composeRule.onNodeWithText("Aún no tienes favoritos").assertDoesNotExist()
    }

    @Test
    fun aFavouriteIsRenderedWithTheSameFormatAsTheMainList() {
        setScreen(
            FavouritesUiState(
                properties = listOf(UiFixtures.property("1", flag = PropertyFlag.FAVOURITE)),
                isLoading = false,
            ),
        )

        // Same title format, same price format, same facts -- because it is the
        // same PropertyFormatter.
        composeRule.onNodeWithText("Piso - Castellana - Madrid").assertIsDisplayed()
        composeRule.onNodeWithText("133 m²").assertIsDisplayed()
        composeRule.onNodeWithText("3 habitaciones").assertIsDisplayed()
    }

    @Test
    fun theCountIsShownAboveTheList() {
        setScreen(
            FavouritesUiState(
                properties = listOf(
                    UiFixtures.property("1", flag = PropertyFlag.FAVOURITE),
                    UiFixtures.property("2", flag = PropertyFlag.FAVOURITE),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("2 favoritos").assertIsDisplayed()
    }

    @Test
    fun theFavouriteButtonAnnouncesWhatTheTapWillDo() {
        setScreen(
            FavouritesUiState(
                properties = listOf(UiFixtures.property("1", flag = PropertyFlag.FAVOURITE)),
                isLoading = false,
            ),
        )

        // The label is the action, not the state -- the state lives in
        // stateDescription.
        composeRule.onNodeWithContentDescription("Quitar de favoritos").assertIsDisplayed()
    }

    @Test
    fun tappingTheFavouriteButtonEmitsTheIntent() {
        val intents = mutableListOf<FavouritesIntent>()
        setScreen(
            state = FavouritesUiState(
                properties = listOf(UiFixtures.property("7", flag = PropertyFlag.FAVOURITE)),
                isLoading = false,
            ),
            onIntent = { intents += it },
        )

        composeRule.onNodeWithContentDescription("Quitar de favoritos").performClick()

        assertThat(intents).containsExactly(FavouritesIntent.FavouriteToggled("7"))
    }

    @Test
    fun tappingDiscardEmitsTheIntent() {
        val intents = mutableListOf<FavouritesIntent>()
        setScreen(
            state = FavouritesUiState(
                properties = listOf(UiFixtures.property("7", flag = PropertyFlag.FAVOURITE)),
                isLoading = false,
            ),
            onIntent = { intents += it },
        )

        composeRule.onNodeWithContentDescription("Borrar del listado").performClick()

        assertThat(intents).containsExactly(FavouritesIntent.DiscardClicked("7"))
    }
}
