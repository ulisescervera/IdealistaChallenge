package com.ulisescervera.uci.core.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ulisescervera.uci.R
import com.ulisescervera.uci.domain.model.PropertyImage

/**
 * Compose image carousel, with the same bottom-right counter as the XML one.
 *
 * The counter is `clearAndSetSemantics {}`: every page already announces
 * "Foto 3 de 7" through its own `contentDescription`, and a second node saying
 * "3 / 7" makes TalkBack repeat itself on every swipe.
 */
@Composable
fun UciImageCarousel(
    images: List<PropertyImage>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onImageClick: ((Int) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (images.isEmpty()) {
        UciEmptyImagePlaceholder(modifier)
        return
    }

    val formatter = LocalPropertyFormatter.current
    val pagerState = rememberPagerState(
        // Clamped: a stale cache can hold fewer photos than the caller expects,
        // and an out-of-range initial page throws.
        initialPage = initialPage.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            // Keeps the neighbouring photo decoded so a swipe is not a grey flash.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = images[page]
            val description = remember(page, images.size, image.localizedName) {
                formatter.imageAccessibilityDescription(page, images.size, image.localizedName)
            }
            AsyncImage(
                model = image.url,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (onImageClick != null) {
                            Modifier.clickable { onImageClick(page) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        if (images.size > 1) {
            Text(
                text = stringResource(
                    R.string.uci_viewer_page_indicator,
                    pagerState.currentPage + 1,
                    images.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(INDICATOR_SCRIM, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clearAndSetSemantics { },
            )
        }

        overlay()
    }
}

@Composable
private fun UciEmptyImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.uci_a11y_property_photo_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Fixed 70% black, not a theme colour: the counter sits on arbitrary
 * photography and only a constant dark backdrop keeps the white label at 4.5:1
 * in both schemes.
 */
private val INDICATOR_SCRIM = Color(0xB3000000)
