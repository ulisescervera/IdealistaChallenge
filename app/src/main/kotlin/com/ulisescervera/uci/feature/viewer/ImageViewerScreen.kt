package com.ulisescervera.uci.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.compose.LocalPropertyFormatter
import com.ulisescervera.uci.core.compose.UciSkeletonBox
import com.ulisescervera.uci.domain.model.PropertyImage

/**
 * The photo viewer, in Compose, exactly as the brief specifies:
 *
 * 1. All photos in a **vertical scroll**, which is always the entry point.
 * 2. Tapping one opens a **horizontal carousel** starting on it.
 * 3. Tapping again **hides the system bars** and turns the remaining background
 *    **black**.
 * 4. The screen **rotates** in every mode.
 *
 * Stateless: it renders an [ImageViewerUiState] and emits
 * [ImageViewerIntent]s. All of the mode state lives in the ViewModel, which is
 * what makes rotation non-destructive -- the composition is thrown away on
 * every orientation change and rebuilt from state.
 */
@Composable
fun ImageViewerScreen(
    state: ImageViewerUiState,
    onIntent: (ImageViewerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The backdrop animates to black in immersive mode so the transition does
    // not flash; the photo is the only thing left on screen.
    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.isPagerMode && state.isImmersive -> Color.Black
            state.isPagerMode -> MaterialTheme.colorScheme.scrim.copy(alpha = PAGER_SCRIM_ALPHA)
            else -> MaterialTheme.colorScheme.background
        },
        label = "uci-viewer-backdrop",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        when {
            state.isLoading -> ViewerSkeleton()

            state.images.isEmpty() -> Text(
                text = stringResource(R.string.uci_a11y_property_photo_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )

            else -> when (val mode = state.mode) {
                ImageViewerUiState.Mode.VerticalList -> VerticalPhotoList(
                    images = state.images,
                    initialIndex = state.initialImageIndex,
                    onPhotoClick = { index -> onIntent(ImageViewerIntent.PhotoOpened(index)) },
                )

                is ImageViewerUiState.Mode.Pager -> PhotoPager(
                    images = state.images,
                    initialIndex = mode.initialIndex,
                    isImmersive = state.isImmersive,
                    onPhotoClick = { onIntent(ImageViewerIntent.ImmersiveToggled) },
                    onDismiss = { onIntent(ImageViewerIntent.PagerDismissed) },
                )
            }
        }
    }
}

@Composable
private fun VerticalPhotoList(
    images: List<PropertyImage>,
    initialIndex: Int,
    onPhotoClick: (Int) -> Unit,
) {
    val formatter = LocalPropertyFormatter.current
    // The list has a hint item at index 0, so photo N lives at index N + 1.
    // Index 0 means "no particular photo": start at the top with the hint
    // visible, rather than scrolling it off screen for nothing.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = when {
            initialIndex <= 0 -> 0
            else -> (initialIndex + 1).coerceAtMost(images.size)
        },
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.uci_viewer_grid_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(items = images, key = { _, image -> image.stableId }) { index, image ->
            AsyncImage(
                model = image.url,
                contentDescription = formatter.imageAccessibilityDescription(
                    position = index,
                    total = images.size,
                    localizedName = image.localizedName,
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // 4:3 matches the source photos, so nothing is cropped away
                    // in the one view whose entire purpose is looking at them.
                    .aspectRatio(PHOTO_ASPECT_RATIO)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPhotoClick(index) },
            )
        }
    }
}

@Composable
private fun PhotoPager(
    images: List<PropertyImage>,
    initialIndex: Int,
    isImmersive: Boolean,
    onPhotoClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = LocalPropertyFormatter.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )

    // Back leaves the pager instead of leaving the screen, which is what the
    // nesting implies: the vertical list is one level up, not the detail.
    BackHandler(onBack = onDismiss)

    // A pinch and a one-finger page swipe are the same gesture arena, so the
    // pager's own drag has to step aside while the visible page is zoomed in
    // -- otherwise a pinch also drags the page underneath it.
    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { isZoomed = false }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !isZoomed,
            key = { index -> images[index].stableId },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = images[page]
            ZoomableImage(
                url = image.url,
                contentDescription = formatter.imageAccessibilityDescription(
                    position = page,
                    total = images.size,
                    localizedName = image.localizedName,
                ),
                onTap = onPhotoClick,
                onZoomChanged = { zoomed -> if (page == pagerState.currentPage) isZoomed = zoomed },
            )
        }

        // The counter is hidden in immersive mode -- nothing but the photo -- and
        // is semantically silent because each page already announces its
        // position.
        if (!isImmersive && images.size > 1) {
            Text(
                text = stringResource(
                    R.string.uci_viewer_page_indicator,
                    pagerState.currentPage + 1,
                    images.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(INDICATOR_SCRIM, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/**
 * A full-screen photo that can be pinch-zoomed.
 *
 * The gesture detector only reacts to *multi*-finger events
 * (`event.changes.size > 1`): a one-finger drag is left untouched so it keeps
 * driving [HorizontalPager]'s swipe, and only a genuine pinch takes over,
 * consuming those pointer changes so the pager does not also see them.
 * [onZoomChanged] is how the pager finds out it should stop accepting swipes
 * while this page is zoomed in -- see the `isZoomed` state in [PhotoPager].
 */
@Composable
private fun ZoomableImage(
    url: String?,
    contentDescription: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        // `Fit`, not `Crop`: in the viewer the whole photo must be visible,
        // and the letterboxing is the black background the brief asks for.
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            scale = (scale * event.calculateZoom()).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = if (scale > MIN_SCALE) offset + event.calculatePan() else Offset.Zero
                            onZoomChanged(scale > MIN_SCALE)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .clickable(
                // No ripple: a splash on a full-screen photo looks like a
                // rendering artefact.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun ViewerSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(SKELETON_PHOTOS) {
            UciSkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

private const val PHOTO_ASPECT_RATIO = 4f / 3f
private const val PAGER_SCRIM_ALPHA = 0.92f
private const val SKELETON_PHOTOS = 3
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private val INDICATOR_SCRIM = Color(0xB3000000)
