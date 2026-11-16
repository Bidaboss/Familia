package fr.familia.app.feature.gifts.received

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.ReceivedGift
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.trips.joinNames

/** « On m'a offert »: the wishes the family gave, and a « Merci » for each. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedGiftsScreen(state: ReceivedGiftsUiState, onBack: () -> Unit = {}, onThank: (itemId: String) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.received_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ReceivedGiftsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ReceivedGiftsUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.received_error_title),
                    body = stringResource(R.string.received_error_body),
                )
                is ReceivedGiftsUiState.Success ->
                    if (state.gifts.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.CardGiftcard,
                            title = stringResource(R.string.received_empty_title),
                            body = stringResource(R.string.received_empty_body),
                        )
                    } else {
                        GiftsContent(state, onThank)
                    }
            }
        }
    }
}

@Composable
private fun GiftsContent(state: ReceivedGiftsUiState.Success, onThank: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.received_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.thankFailed) {
            item(key = "failed") {
                Text(
                    stringResource(R.string.received_thank_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        items(state.gifts, key = { it.id }) { gift -> GiftCard(gift, onThank = { onThank(gift.id) }) }
    }
}

@Composable
private fun GiftCard(gift: ReceivedGift, onThank: () -> Unit) {
    ClayCard(tint = ClayTint.Birthday) {
        gift.imageUrl?.let { image ->
            // The picture of the shop page (récit 8 bis): decorative.
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().heightIn(max = 160.dp).clip(MaterialTheme.shapes.medium),
            )
        }
        Text(gift.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val givers = joinNames(gift.givers)
        if (givers.isNotEmpty()) {
            Text(
                stringResource(if (gift.joint) R.string.received_from_joint else R.string.received_from, givers),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        occasionText(gift)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(
            onClick = onThank,
            enabled = !gift.thanked,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp),
        ) {
            if (gift.thanked) {
                Text(stringResource(R.string.received_thanked))
            } else {
                Icon(Icons.Outlined.Favorite, contentDescription = null)
                Text(stringResource(R.string.received_thank), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun occasionText(gift: ReceivedGift): String? {
    val occasion = gift.occasion ?: return null
    val label = occasionLabel(occasion)
    return gift.year?.let { "$label $it" } ?: label
}

/** Fictional data for previews and screenshots. */
internal fun previewReceivedGifts(): ReceivedGiftsUiState.Success = ReceivedGiftsUiState.Success(
    listOf(
        ReceivedGift("r1", "Une machine à pain", null, null, listOf("Camille", "Jules"), joint = true, GiftOccasion.Birthday, 2027, thanked = false),
        ReceivedGift("r2", "Un roman policier", null, null, listOf("Jules"), joint = false, GiftOccasion.Christmas, 2026, thanked = true),
    ),
)

@FamiliaPreviews
@Composable
private fun ReceivedGiftsScreenPreview() {
    FamiliaTheme { Surface { ReceivedGiftsScreen(previewReceivedGifts()) } }
}

@FamiliaPreviews
@Composable
private fun ReceivedGiftsEmptyPreview() {
    FamiliaTheme { Surface { ReceivedGiftsScreen(ReceivedGiftsUiState.Success(emptyList())) } }
}
