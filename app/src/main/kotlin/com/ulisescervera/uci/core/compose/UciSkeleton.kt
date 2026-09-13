package com.ulisescervera.uci.core.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ulisescervera.uci.core.theme.UciBrand
import com.ulisescervera.uci.core.ui.skeleton.animationsEnabled

/**
 * The Compose twin of `SkeletonView`, for the favourites screen.
 *
 * It behaves identically in the two ways that matter:
 *
 * - **It respects "remove animations".** When the system animator scale is 0 the
 *   box renders as a flat block instead of sweeping, because a perpetual
 *   gradient is a documented migraine and vestibular trigger.
 * - **It is invisible to screen readers.** `clearAndSetSemantics {}` removes the
 *   node entirely; the *screen* announces "cargando", not each placeholder.
 */
@Composable
fun UciSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) UciBrand.skeletonBaseDark else UciBrand.skeletonBaseLight
    val highlight = if (dark) UciBrand.skeletonHighlightDark else UciBrand.skeletonHighlightLight

    val context = LocalContext.current
    val animated = remember(context) { context.animationsEnabled() }

    // The sweep travels a little more than the screen width so the highlight
    // enters and leaves instead of appearing mid-box.
    val sweepWidth = with(LocalConfiguration.current) { screenWidthDp.toFloat() * 2f }

    val brush = if (animated) {
        val transition = rememberInfiniteTransition(label = "uci-skeleton")
        val offset by transition.animateFloat(
            initialValue = -sweepWidth,
            targetValue = sweepWidth,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SWEEP_DURATION_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "uci-skeleton-offset",
        )
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(offset, 0f),
            end = Offset(offset + sweepWidth / 2f, 0f),
        )
    } else {
        Brush.linearGradient(colors = listOf(base, base))
    }

    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .background(brush = brush, shape = shape),
    )
}

private const val SWEEP_DURATION_MILLIS = 1_150
