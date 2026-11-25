package fr.familia.app.feature.trips.place

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.place.FoundPlace
import fr.familia.app.core.place.PLACE_SEARCH_RADIUS
import fr.familia.app.core.place.distanceLabel
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.detail.TripPathColors

/**
 * The search dedicated to each kind of place (récit 103, règle 20): a field, the places found
 * around the destination, nearest first, and « Ajouter « … » » for a name it does not know. A touch
 * on a place opens its page (règle 21). Stateless.
 */
@Composable
fun PlaceSearchScreen(
    state: PlaceSearchUiState,
    destination: String,
    onType: (String) -> Unit,
    onRetry: () -> Unit,
    onChoose: (FoundPlace) -> Unit,
    onChooseTyped: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    TripFormScaffold(
        title = stringResource(
            when (state.kind) {
                TripPlaceKind.Lodging -> R.string.place_search_lodging
                TripPlaceKind.Restaurant -> R.string.place_search_restaurant
                TripPlaceKind.Spot -> R.string.place_search_spot
            },
        ),
        help = ScreenHelp.TripStepEdit,
        onBack = onBack,
        action = null,
        busy = false,
        onAction = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TripTile(Icons.Outlined.Search, placeKindLabel(state.kind)) {
                val label = stringResource(R.string.place_search_field)
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onType,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(label) },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
                FormHint(
                    if (destination.isEmpty()) {
                        stringResource(R.string.place_search_hint_nowhere)
                    } else {
                        stringResource(R.string.place_search_hint, PLACE_SEARCH_RADIUS / 1000, destination)
                    },
                )
            }

            val typed = state.text.trim()
            if (typed.isNotEmpty()) {
                SearchRow(
                    icon = Icons.Outlined.Add,
                    title = stringResource(R.string.place_search_add_typed, typed),
                    detail = stringResource(R.string.place_search_add_typed_hint),
                    onClick = { onChooseTyped(typed) },
                )
            }

            when (val results = state.results) {
                PlaceSearchResults.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    SkyHint(stringResource(R.string.place_search_loading))
                }
                PlaceSearchResults.NoPoint -> SkyHint(stringResource(R.string.place_search_no_point))
                PlaceSearchResults.Failed -> Column {
                    SkyHint(stringResource(R.string.place_search_failed))
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                }
                is PlaceSearchResults.Found -> if (results.places.isEmpty()) {
                    SkyHint(stringResource(if (typed.isEmpty()) R.string.place_search_none_around else R.string.place_search_none))
                } else {
                    results.places.forEach { place ->
                        val distance = distanceLabel(place.meters)
                        SearchRow(
                            icon = placeKindIcon(state.kind),
                            title = place.name,
                            detail = listOfNotNull(place.genre, distance, place.address).joinToString(" · "),
                            onClick = { onChoose(place) },
                        )
                    }
                }
            }
            SkyHint(stringResource(R.string.place_search_source))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A line written on the sky of the trips, out of a tile: always dark, in light and dark themes alike. */
@Composable
private fun SkyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
}

/** One choice, at least 56 dp high; clickable reads its lines in one go to TalkBack. */
@Composable
private fun SearchRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fictional data for previews, screenshots and the accessibility tests. */
internal fun previewPlaceSearchState(
    kind: TripPlaceKind = TripPlaceKind.Restaurant,
    text: String = "",
    results: PlaceSearchResults = PlaceSearchResults.Found(
        listOf(
            FoundPlace("Le Bistrot du Port", "Restaurant", "4 quai de la Citadelle, Ajaccio", 41.9192, 8.7386, 350),
            FoundPlace("U Pampasgiolu", "Restaurant", null, 41.9205, 8.7371, 1_200),
        ),
    ),
) = PlaceSearchUiState(kind = kind, text = text, results = results)

@FamiliaPreviews
@Composable
private fun PlaceSearchScreenPreview() {
    FamiliaTheme { Surface { PlaceSearchScreen(previewPlaceSearchState(), "Ajaccio", {}, {}, {}, {}, {}) } }
}
