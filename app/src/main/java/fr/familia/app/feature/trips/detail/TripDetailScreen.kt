package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripSectionTitle
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel
import fr.familia.app.feature.trips.tripDatesLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** One trip: its dates, who takes part, the steps and « bien arrivé ». Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    state: TripDetailUiState,
    onRetry: () -> Unit,
    onEditTrip: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onArrived: (stepId: String) -> Unit,
    onClearArrival: (stepId: String) -> Unit,
    onBack: () -> Unit,
) {
    val success = state as? TripDetailUiState.Success
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(success?.trip?.title ?: stringResource(R.string.trip_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (success?.canManage == true) {
                        TextButton(onClick = onEditTrip, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.trip_edit))
                        }
                    }
                    HelpButton(ScreenHelp.TripDetail)
                },
            )
        },
        floatingActionButton = {
            if (success?.isParticipant == true) {
                ExtendedFloatingActionButton(
                    onClick = onAddStep,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.trip_add_step)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TripDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                TripDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trip_missing_title),
                    body = stringResource(R.string.trip_missing_body),
                )
                TripDetailUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trips_error_title),
                    body = stringResource(R.string.trips_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is TripDetailUiState.Success -> Content(state, onEditStep, onArrived, onClearArrival)
            }
        }
    }
}

@Composable
private fun Content(
    state: TripDetailUiState.Success,
    onEditStep: (String) -> Unit,
    onArrived: (String) -> Unit,
    onClearArrival: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the add button never hides the last step.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "summary") { Summary(state) }
        item(key = "steps-title") { TripSectionTitle(stringResource(R.string.trip_steps)) }
        if (state.sendFailed) {
            item(key = "failed") {
                Text(
                    stringResource(R.string.trip_arrival_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.steps.isEmpty()) {
            item(key = "no-steps") {
                Text(
                    stringResource(if (state.isParticipant) R.string.trip_no_steps_participant else R.string.trip_no_steps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.steps, key = { "step-${it.id}" }) { step ->
            StepCard(
                step = step,
                today = state.today,
                isParticipant = state.isParticipant,
                onEdit = { onEditStep(step.id) },
                onArrived = { onArrived(step.id) },
                onClearArrival = { onClearArrival(step.id) },
            )
        }
    }
}

@Composable
private fun Summary(state: TripDetailUiState.Success) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Family)
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Family) {
        Text(tripDatesLabel(state.trip, state.today), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.trip_with, joinNames(state.participants)),
            style = MaterialTheme.typography.bodyLarge,
            color = variant,
        )
        Text(
            pluralStringResource(R.plurals.trip_followers, state.followers, state.followers),
            style = MaterialTheme.typography.bodyMedium,
            color = variant,
        )
        Text(
            stringResource(R.string.trip_created_by, state.trip.creatorName),
            style = MaterialTheme.typography.bodyMedium,
            color = variant,
        )
    }
}

@Composable
private fun StepCard(
    step: TripStep,
    today: LocalDate,
    isParticipant: Boolean,
    onEdit: () -> Unit,
    onArrived: () -> Unit,
    onClearArrival: () -> Unit,
) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isParticipant) onEdit else null,
        onClickLabel = stringResource(R.string.trip_step_edit_action),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(stepTypeIcon(step.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stepTypeLabel(step.type), style = MaterialTheme.typography.labelLarge, color = variant)
                Text(step.place, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.trip_step_depart, whenLabel(step.departDate, step.departTime, today)),
            style = MaterialTheme.typography.bodyMedium,
            color = variant,
        )
        if (step.arriveDate != null || step.arriveTime != null) {
            Text(
                stringResource(R.string.trip_step_arrive, whenLabel(step.arriveDate ?: step.departDate, step.arriveTime, today)),
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
            )
        }
        step.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(8.dp))
        val arrival = step.arrival
        if (arrival != null) {
            ArrivedRow(arrival, today, isParticipant, onClearArrival)
        } else if (isParticipant) {
            FilledTonalButton(onClick = onArrived, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_arrived_action))
            }
        } else {
            Text(stringResource(R.string.trip_not_arrived), style = MaterialTheme.typography.bodyMedium, color = variant)
        }
    }
}

/** « Bien arrivé, signalé par Lou à 9 h 52 », with « Annuler » for a participant. */
@Composable
private fun ArrivedRow(arrival: TripArrival, today: LocalDate, isParticipant: Boolean, onClearArrival: () -> Unit) {
    val at = arrival.at?.atZone(ZoneId.systemDefault())
    val label = if (at == null) {
        stringResource(R.string.trip_arrived_by, arrival.name)
    } else {
        stringResource(R.string.trip_arrived_by_at, arrival.name, whenLabel(at.toLocalDate(), at.toLocalTime(), today))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    }
    if (isParticipant) {
        TextButton(onClick = onClearArrival, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.trip_arrived_undo))
        }
    }
}

/** "samedi 10 juillet, 8 h 15", or the day alone; it always follows other words. */
@Composable
private fun whenLabel(day: LocalDate, time: LocalTime?, today: LocalDate): String {
    val date = formatDay(day, today).lowercase(Locale.FRANCE)
    return if (time == null) date else stringResource(R.string.trip_day_time, date, formatTime(time))
}

/** Fictional trip for previews and screenshots. */
internal fun previewTripDetail(isParticipant: Boolean = true): TripDetailUiState.Success {
    val start = LocalDate.of(2027, 8, 2)
    return TripDetailUiState.Success(
        trip = Trip("1", "Corse", start, LocalDate.of(2027, 8, 16), listOf("a", "b", "c"), listOf("a", "b", "c", "d"), "a", "Camille"),
        participants = listOf("Camille", "Inès", "Noé"),
        followers = 11,
        steps = listOf(
            TripStep(
                "s1", StepType.Flight, "Ajaccio", start, LocalTime.of(8, 15), start, LocalTime.of(9, 40), "Vol AF 7520",
                arrival = TripArrival(Instant.parse("2027-08-02T07:52:00Z"), "b", "Inès"),
            ),
            TripStep("s2", StepType.Lodging, "Gîte de Porto", start, LocalTime.of(16, 0), LocalDate.of(2027, 8, 9), null, null),
            TripStep("s3", StepType.Car, "Bonifacio", LocalDate.of(2027, 8, 9), LocalTime.of(10, 0), LocalDate.of(2027, 8, 9), LocalTime.of(13, 30), null),
        ),
        canManage = true,
        isParticipant = isParticipant,
        today = start,
    )
}

@FamiliaPreviews
@Composable
private fun TripDetailScreenPreview() {
    FamiliaTheme { Surface { TripDetailScreen(previewTripDetail(), {}, {}, {}, {}, {}, {}, {}) } }
}
