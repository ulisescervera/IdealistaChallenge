package com.ulisescervera.uci.core.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.core.theme.UciBrand
import com.ulisescervera.uci.domain.model.Property

/**
 * A property card in Compose, matching the XML row of the main list.
 *
 * The brief asks for the favourites tab to use "el mismo formato que el listado
 * de inmuebles", and this is the honest way to do it in a hybrid app: the same
 * [com.ulisescervera.uci.core.format.PropertyFormatter] produces the title, the
 * price, the €/m² and the facts, so the two lists cannot disagree about how a
 * property reads. Only the rendering differs.
 *
 * Accessibility mirrors the XML row too: the text block is one merged node with
 * the full description, and the buttons are separate, labelled targets with a
 * state description.
 */
@Composable
fun UciPropertyCard(
    property: Property,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
) {
    val formatter = LocalPropertyFormatter.current
    val title = formatter.title(property)
    val price = formatter.price(property.price)
    val pricePerMeter = formatter.pricePerSquareMeter(property)
    val facts = formatter.facts(property)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        // Same reasoning as `Widget.Uci.Card.Property` on the XML side: a
        // white card in light mode, distinguished from `uci_background` by
        // elevation alone rather than a tinted container. Dark mode keeps
        // `colorScheme.surfaceContainer`, which already reads as "raised"
        // there (see uci_property_card_background in colors.xml).
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.uci_property_card_background)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        UciImageCarousel(
            images = property.carouselImages,
            onImageClick = onImageClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(CAROUSEL_HEIGHT),
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // One utterance for the whole block, exactly as the XML row does
                // with `mergeChildrenForAccessibility`.
                .semantics(mergeDescendants = true) {
                    contentDescription = formatter.accessibilityDescription(property)
                },
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (pricePerMeter != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pricePerMeter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                UciFactsRow(facts)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The label says what the tap will do; the state description says
            // what is true now. TalkBack reads both, and merging them into one
            // word ("favorito") communicates neither.
            val favouriteState = stringResource(
                if (property.isFavourite) R.string.uci_a11y_favourite_state_on
                else R.string.uci_a11y_favourite_state_off,
            )
            IconButton(
                onClick = onFavouriteClick,
                modifier = Modifier.semantics { stateDescription = favouriteState },
            ) {
                Icon(
                    painter = painterResource(
                        if (property.isFavourite) R.drawable.ic_uci_favourite_filled
                        else R.drawable.ic_uci_favourite,
                    ),
                    contentDescription = stringResource(
                        if (property.isFavourite) R.string.uci_action_favourite_remove
                        else R.string.uci_action_favourite_add,
                    ),
                    tint = if (property.isFavourite) {
                        UciBrand.favourite()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onDiscardClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_uci_delete),
                    contentDescription = stringResource(R.string.uci_action_discard),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The facts row.
 *
 * `FlowRow` rather than `Row`: at a 200% font scale "133 m² · 3 habitaciones ·
 * 2 baños · 2ª planta · Con ascensor · Garaje incluido" cannot fit on one line,
 * and a non-wrapping row would clip it. Truncating factual information because
 * the user needs large text is not acceptable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UciFactsRow(facts: List<PropertyFormatter.Fact>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            // The labels are already inside the card's merged description.
            .clearAndSetSemantics { },
    ) {
        facts.forEach { fact ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(fact.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = fact.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CAROUSEL_HEIGHT = 216.dp
