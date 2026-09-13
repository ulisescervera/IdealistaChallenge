package com.ulisescervera.uci.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The Compose half of the theme.
 *
 * UCI is a hybrid app: the list and the detail are XML views themed by
 * `values/themes.xml`, while the image viewer and the favourites tab are
 * Compose. Two theming systems means two chances to drift, so the hex values
 * below are the *same* ones as `res/values/colors.xml` and
 * `res/values-night/colors.xml`, one role at a time.
 *
 * ### Why not read the XML attributes at runtime?
 * It is possible (`MaterialTheme` from an `AppCompat` context via
 * `androidx.compose.material3:material3` + `rememberColorScheme`-style helpers)
 * and it is tempting. It is rejected here because it makes the Compose previews
 * depend on an Activity theme, which breaks `@Preview` -- and a Compose screen
 * you cannot preview is a Compose screen nobody will refactor. The duplication
 * is 30 lines and is guarded by the comment in colors.xml.
 *
 * ### Why no dynamic colour?
 * The contrast ratios in `colors.xml` were verified against WCAG AA. A
 * wallpaper-derived palette is not verifiable, and on the favourite accent
 * specifically it would fight the brand signal.
 */
@Composable
fun UciTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) UciDarkColors else UciLightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Mirrors res/values/colors.xml. */
private val UciLightColors = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F6),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4C5F7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF051C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1D),
    surface = Color(0xFFF5FAFB),
    onSurface = Color(0xFF171D1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    surfaceContainer = Color(0xFFE9EFEF),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8C9),
    inverseSurface = Color(0xFF2B3131),
    inverseOnSurface = Color(0xFFECF2F2),
)

/** Mirrors res/values-night/colors.xml. */
private val UciDarkColors = darkColorScheme(
    primary = Color(0xFF80D5DA),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9CF1F6),
    secondary = Color(0xFFB0CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB4C8E9),
    onTertiary = Color(0xFF1D314C),
    tertiaryContainer = Color(0xFF344764),
    onTertiaryContainer = Color(0xFFD4E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE4E4),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainer = Color(0xFF1B2122),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4849),
    inverseSurface = Color(0xFFDEE4E4),
    inverseOnSurface = Color(0xFF2B3131),
)

/**
 * The favourite accent, which is intentionally outside the Material roles: it
 * is a brand signal that must stay recognisable in both schemes rather than
 * blend into the surface.
 */
object UciBrand {
    val favouriteLight = Color(0xFFD81B60)
    val favouriteDark = Color(0xFFFF87A9)

    @Composable
    fun favourite(): Color = if (isSystemInDarkTheme()) favouriteDark else favouriteLight

    val skeletonBaseLight = Color(0xFFE1E7E8)
    val skeletonHighlightLight = Color(0xFFF3F7F8)
    val skeletonBaseDark = Color(0xFF242B2C)
    val skeletonHighlightDark = Color(0xFF333B3C)
}
