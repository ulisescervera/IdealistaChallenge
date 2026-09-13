package com.ulisescervera.uci.feature.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.compose.UciPropertyCard
import com.ulisescervera.uci.core.compose.UciSkeletonBox
import com.ulisescervera.uci.core.theme.UciTheme
import com.ulisescervera.uci.domain.model.Property

/**
 * Favourites, in Compose, with the same card as the XML list.
 *
 * Stateless by design: it takes a [FavouritesUiState] and emits
 * [FavouritesIntent]s, so `@Preview` can render every branch (loading, empty,
 * populated) without a ViewModel, a database or a network.
 *
 * No `TopAppBar` here on purpose: the title ("Favoritos") is the Activity's
 * shared toolbar now, driven by the nav graph's destination label like every
 * other screen. The one thing that toolbar cannot show for us is the count --
 * that is per-state, not per-destination -- so it stays here, as a plain line
 * above the list instead of a Compose app bar's subtitle.
 */
@Composable
fun FavouritesScreen(
    state: FavouritesUiState,
    onIntent: (FavouritesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.properties.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.uci_favourites_count,
                    state.properties.size,
                    state.properties.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> FavouritesSkeleton()
                state.isEmpty -> FavouritesEmptyState()
                else -> FavouritesList(state.properties, onIntent)
            }
        }
    }
}

@Composable
private fun FavouritesList(
    properties: List<Property>,
    onIntent: (FavouritesIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            // Room for the bottom navigation bar, which draws over this screen.
            bottom = 96.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Keyed by property id, so recomposition after an un-favourite animates
        // the right row out instead of re-binding every card below it.
        items(items = properties, key = { it.id }) { property ->
            UciPropertyCard(
                property = property,
                onClick = { onIntent(FavouritesIntent.PropertyClicked(property.id)) },
                onFavouriteClick = { onIntent(FavouritesIntent.FavouriteToggled(property.id)) },
                onDiscardClick = { onIntent(FavouritesIntent.DiscardClicked(property.id)) },
                onImageClick = { onIntent(FavouritesIntent.PropertyClicked(property.id)) },
            )
        }
    }
}

/**
 * Loading placeholder. The container carries the single "cargando" description
 * and the boxes themselves are semantically invisible -- see [UciSkeletonBox].
 */
@Composable
private fun FavouritesSkeleton(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.uci_a11y_loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(SKELETON_CARDS) {
            UciSkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
        }
    }
}

@Composable
private fun FavouritesEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.uci_favourites_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.uci_favourites_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val SKELETON_CARDS = 3

@Preview(name = "Vacío", showBackground = true)
@Composable
private fun FavouritesEmptyPreview() {
    UciTheme {
        FavouritesEmptyState()
    }
}

@Preview(name = "Cargando", showBackground = true)
@Composable
private fun FavouritesLoadingPreview() {
    UciTheme {
        FavouritesSkeleton()
    }
}
