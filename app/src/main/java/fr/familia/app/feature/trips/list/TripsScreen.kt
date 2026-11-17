package fr.familia.app.feature.trips.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Trip
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripSectionTitle
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.tripDatesLabel
import java.time.LocalDate

/** The trips the member follows, and « Nouveau voyage ». Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    state: TripsUiState,
    onRetry: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onAddTrip: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbums: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Trips) },
            )
        },
        floatingActionButton = {
            if (state is TripsUiState.Success && state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = onAddTrip,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.trips_add)) },
                    text = { Text(stringResource(R.string.trips_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TripsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                TripsUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trips_error_title),
                    body = stringResource(R.string.trips_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is TripsUiState.Success -> if (state.isEmpty) {
                    EmptyState(
                        icon = Icons.Outlined.CardTravel,
                        title = stringResource(R.string.trips_empty_title),
                        body = stringResource(if (state.canCreate) R.string.trips_empty_body else R.string.trips_empty_body_child),
                        // « Nouveau voyage » is the button below; a child, who has none, gets the albums instead.
                        action = if (onOpenAlbums != null && !state.canCreate) {
                            { ClayLink(stringResource(R.string.trips_empty_albums), onClick = onOpenAlbums) }
                        } else {
                            null
                        },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room at the bottom so the add button never hides the last trip.
                        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                    ) {
                        // The trip under way, or else the next one, is the point of the screen (DESIGN-11).
                        val highlight = (state.ongoing.firstOrNull() ?: state.upcoming.firstOrNull())?.trip?.id
                        section(R.string.trips_ongoing, "ongoing", state.ongoing, state.today, highlight, onOpenTrip)
                        section(R.string.trips_upcoming, "upcoming", state.upcoming, state.today, highlight, onOpenTrip)
                        section(R.string.trips_past, "past", state.past, state.today, highlight, onOpenTrip)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    key: String,
    trips: List<TripSummary>,
    today: LocalDate,
    highlight: String?,
    onOpenTrip: (String) -> Unit,
) {
    if (trips.isEmpty()) return
    item(key = "title-$key") { TripSectionTitle(stringResource(titleRes)) }
    items(trips, key = { "trip-${it.trip.id}" }) { summary -> TripCard(summary, today, emphasis = summary.trip.id == highlight, onClick = { onOpenTrip(summary.trip.id) }) }
}

@Composable
private fun TripCard(summary: TripSummary, today: LocalDate, emphasis: Boolean, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.trips_open_action),
        emphasis = emphasis,
    ) {
        ClayCardTitle(summary.trip.title, style = MaterialTheme.typography.titleMedium)
        Text(
            tripDatesLabel(summary.trip, today),
            style = MaterialTheme.typography.bodyMedium,
            color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
        )
        if (summary.participants.isNotEmpty()) {
            Text(
                stringResource(R.string.trip_with, joinNames(summary.participants)),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
    }
}

/** Fictional trips for previews and screenshots. */
internal fun previewTrips(): TripsUiState.Success {
    fun trip(id: String, title: String, start: LocalDate, end: LocalDate) =
        Trip(id, title, start, end, listOf("a", "b"), listOf("a", "b", "c"), "a", "Camille")
    return TripsUiState.Success(
        ongoing = listOf(TripSummary(trip("1", "Week-end à Lyon", LocalDate.of(2027, 7, 9), LocalDate.of(2027, 7, 11)), listOf("Camille", "Inès"))),
        upcoming = listOf(TripSummary(trip("2", "Corse", LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 16)), listOf("Camille", "Inès", "Noé"))),
        past = listOf(TripSummary(trip("3", "Ski à Morzine", LocalDate.of(2027, 2, 13), LocalDate.of(2027, 2, 20)), listOf("Inès"))),
        today = LocalDate.of(2027, 7, 10),
    )
}

@FamiliaPreviews
@Composable
private fun TripsScreenPreview() {
    FamiliaTheme { Surface { TripsScreen(previewTrips(), {}, {}, {}, {}) } }
}
