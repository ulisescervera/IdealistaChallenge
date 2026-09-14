package com.ulisescervera.uci.feature.viewer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.core.compose.LocalPropertyFormatter
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.theme.UciTheme
import com.ulisescervera.uci.support.UiFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Compose image viewer.
 *
 * The two modes the brief describes are asserted directly: a vertical list of
 * every photo, and a horizontal pager reached by tapping one. Because the screen
 * is stateless, "the pager is open on photo 3" is a state, not a gesture
 * sequence -- which is what makes these tests fast and non-flaky.
 */
@RunWith(AndroidJUnit4::class)
class ImageViewerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val formatter = PropertyFormatter(ApplicationProvider.getApplicationContext())

    private val images = listOf(
        UiFixtures.image("a"),
        UiFixtures.image("b"),
        UiFixtures.image("c"),
    )

    private fun setScreen(
        state: ImageViewerUiState,
        onIntent: (ImageViewerIntent) -> Unit = {},
    ) {
        composeRule.setContent {
            UciTheme {
                CompositionLocalProvider(LocalPropertyFormatter provides formatter) {
                    ImageViewerScreen(state = state, onIntent = onIntent)
                }
            }
        }
    }

    @Test
    fun theVerticalListShowsEveryPhotoWithItsPosition() {
        setScreen(
            ImageViewerUiState(
                propertyId = "1",
                images = images,
                mode = ImageViewerUiState.Mode.VerticalList,
                isLoading = false,
            ),
        )

        // The backend's own translation plus the position, so a screen-reader
        // user knows both what and where.
        composeRule.onNodeWithContentDescription("Foto 1 de 3: Salón").assertIsDisplayed()
    }

    @Test
    fun tappingAPhotoRequestsThePager() {
        val intents = mutableListOf<ImageViewerIntent>()
        setScreen(
            state = ImageViewerUiState(
                propertyId = "1",
                images = images,
                mode = ImageViewerUiState.Mode.VerticalList,
                isLoading = false,
            ),
            onIntent = { intents += it },
        )

        composeRule.onNodeWithContentDescription("Foto 2 de 3: Salón").performClick()

        assertThat(intents).containsExactly(ImageViewerIntent.PhotoOpened(1))
    }

    @Test
    fun thePagerShowsTheCounter() {
        setScreen(
            ImageViewerUiState(
                propertyId = "1",
                images = images,
                mode = ImageViewerUiState.Mode.Pager(0),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun tappingInThePagerRequestsImmersiveMode() {
        val intents = mutableListOf<ImageViewerIntent>()
        setScreen(
            state = ImageViewerUiState(
                propertyId = "1",
                images = images,
                mode = ImageViewerUiState.Mode.Pager(0),
                isLoading = false,
            ),
            onIntent = { intents += it },
        )

        composeRule.onAllNodesWithContentDescription("Foto 1 de 3: Salón")[0].performClick()

        assertThat(intents).containsExactly(ImageViewerIntent.ImmersiveToggled)
    }

    @Test
    fun theCounterIsHiddenInImmersiveMode() {
        setScreen(
            ImageViewerUiState(
                propertyId = "1",
                images = images,
                mode = ImageViewerUiState.Mode.Pager(0),
                isImmersive = true,
                isLoading = false,
            ),
        )

        // Nothing but the photo on a black backdrop.
        composeRule.onNodeWithText("1 / 3").assertDoesNotExist()
    }

    @Test
    fun aPropertyWithNoPhotosSaysSoInsteadOfShowingABlankScreen() {
        setScreen(
            ImageViewerUiState(propertyId = "1", images = emptyList(), isLoading = false),
        )

        composeRule.onNodeWithText("Este inmueble no tiene fotos").assertIsDisplayed()
    }

    @Test
    fun aSinglePhotoHasNoCounterToShow() {
        setScreen(
            ImageViewerUiState(
                propertyId = "1",
                images = listOf(images.first()),
                mode = ImageViewerUiState.Mode.Pager(0),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("1 / 1").assertDoesNotExist()
    }
}
