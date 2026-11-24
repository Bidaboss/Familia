package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.place.placeKindIcon
import fr.familia.app.feature.trips.place.placeKindLabel
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel
import java.time.LocalDate
import kotlinx.coroutines.delay

/** What « Le trajet » does: open a step, change the nights of a stay, take back a change. */
class TimelineActions(
    val onOpen: (stepId: String) -> Unit = {},
    /** Null for who does not go: they see the nights, without − and +. */
    val onNights: ((stayId: String, delta: Int) -> NightsChange)? = null,
    val onUndo: (List<TripStep>) -> Unit = {},
    /** The places programmed under each destination, by its step id (règle 18), from [placesByStep]. */
    val places: Map<String, List<TripPlace>> = emptyMap(),
    val onOpenPlace: (placeId: String) -> Unit = {},
    /** Null for who does not go: they see the programme, without « + ». */
    val onAddPlace: ((stepId: String, kind: TripPlaceKind) -> Unit)? = null,
)

/** The places of each destination, the accommodation first, then the restaurants and the spots, each by time then name. */
internal fun placesByStep(places: List<TripPlace>): Map<String, List<TripPlace>> =
    places.groupBy { it.stepId }.mapValues { (_, list) ->
        list.sortedWith(compareBy<TripPlace>({ it.kind.ordinal }, { it.arriveTime ?: java.time.LocalTime.MAX }, { it.name.lowercase() }))
    }

private const val UNDO_MILLIS = 5_000L

/**
 * « Le trajet » (règles 7, 16, 18, DESIGN-68 A): « Le voyage commence », the destinations numbered
 * 1 to n with their dates and their nights (− and + for those who go), the transports between
 * with their distance, « Le voyage se termine ». After a change of nights, « Annuler » for 5 s.
 */
@Composable
fun TripTimelineSection(trip: Trip, steps: List<TripStep>, actions: TimelineActions, modifier: Modifier = Modifier) {
    val line = remember(trip, steps) { tripTimeline(trip, steps) }
    var undo by remember { mutableStateOf<Pair<String, List<TripStep>>?>(null) }
    var blocked by remember { mutableStateOf<Pair<String, NightsBlock>?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MILLIS)
            undo = null
        }
    }
    // A white card with dark ink in every theme: the sheet behind it is the sky of the trip.
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.timeline_title),
            color = Ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        EndRow(
            text = stringResource(R.string.timeline_start),
            day = trip.startDate,
            trailing = {
                val description = stringResource(R.string.timeline_planned_a11y, line.planned, line.total)
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { if (line.total == 0) 1f else (line.planned.toFloat() / line.total).coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF17333F),
                        trackColor = Color(0xFFDCE3E8),
                    )
                    Text(
                        stringResource(R.string.timeline_planned, line.planned, line.total),
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            },
        )
        if (line.missing > 0 && line.destinations.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.timeline_missing, line.missing, line.missing),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF3D2E00),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE9A8)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        undo?.let { (name, before) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF17333F)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timeline_changed, name), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.onUndo(before); undo = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.timeline_undo), color = Color(0xFFFFD84A), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (line.rows.isEmpty()) {
            Text(stringResource(R.string.timeline_empty), style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        line.rows.forEach { row ->
            when (row) {
                is TimelineRow.Destination -> DestinationRow(
                    row = row,
                    plusBlocked = changeNights(trip, steps, row.step, 1) is NightsChange.Blocked,
                    actions = actions,
                    blocked = blocked?.takeIf { it.first == row.step.id }?.second,
                    onChange = { delta ->
                        val onNights = actions.onNights ?: return@DestinationRow
                        when (val change = onNights(row.step.id, delta)) {
                            is NightsChange.Steps -> { undo = placeName(row.step) to change.changed; blocked = null }
                            is NightsChange.Blocked -> blocked = row.step.id to change.why
                        }
                    },
                )
                is TimelineRow.Move -> MoveRow(row, onOpen = { actions.onOpen(row.step.id) })
            }
        }
        EndRow(text = stringResource(R.string.timeline_end), day = trip.endDate, trailing = {})
    }
}

/** « Bordeaux » out of « Bordeaux, Gironde, France ». */
internal fun placeName(step: TripStep): String = step.place.substringAfterLast('→').substringBefore(',').trim().ifEmpty { step.place }

private fun shortDay(day: LocalDate): String = frenchDate(day, "EEE d MMM")

@Composable
private fun EndRow(text: String, day: LocalDate, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF17333F)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(shortDay(day), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
        trailing()
    }
}

@Composable
private fun DestinationRow(row: TimelineRow.Destination, plusBlocked: Boolean, actions: TimelineActions, blocked: NightsBlock?, onChange: (Int) -> Unit) {
    val name = placeName(row.step)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF3F6F8)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The number is the one of the round on the map (règle 7).
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF111111)).border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timeline_open, name)) { actions.onOpen(row.step.id) }
                    .heightIn(min = 56.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("$name ›", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.timeline_dates, shortDay(row.start), shortDay(row.end)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canChange = actions.onNights != null
            if (canChange) {
                val less = stringResource(R.string.timeline_less_a11y, name)
                OutlinedButton(
                    onClick = { onChange(-1) },
                    enabled = row.nights > 1,
                    modifier = Modifier.size(56.dp).semantics { contentDescription = less },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = nightsButtonColors(),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            }
            Text(
                pluralStringResource(R.plurals.timeline_nights, row.nights, row.nights),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.width(96.dp),
            )
            if (canChange) {
                val more = stringResource(R.string.timeline_more_a11y, name)
                OutlinedButton(
                    onClick = { onChange(1) },
                    enabled = !plusBlocked,
                    modifier = Modifier.size(56.dp).semantics { contentDescription = more },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = nightsButtonColors(),
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        if (actions.onNights != null && (plusBlocked || blocked != null)) {
            Text(
                stringResource(if (blocked == NightsBlock.OneNight) R.string.timeline_one_night else R.string.timeline_at_return),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
        DestinationProgramme(row.step.id, name, actions)
    }
}

/**
 * Under a destination, in smaller type (règle 18): its accommodation, restaurants and spots, each
 * opening its page (règle 19); then « + Hébergement », « + Restaurant », « + Spot » for those who go (règle 20).
 */
@Composable
private fun DestinationProgramme(stepId: String, destination: String, actions: TimelineActions) {
    val places = actions.places[stepId].orEmpty()
    places.forEach { place ->
        val kind = placeKindLabel(place.kind)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timeline_open, place.name)) { actions.onOpenPlace(place.id) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(placeKindIcon(place.kind), contentDescription = null, tint = InkSoft, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("${place.name} ›", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = place.arriveTime?.let { " · " + it.format(java.time.format.DateTimeFormatter.ofPattern("HH'h'mm")) }.orEmpty()
                Text(kind + time, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
    val onAdd = actions.onAddPlace ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TripPlaceKind.entries.forEach { kind ->
            val label = placeKindLabel(kind)
            val spoken = stringResource(R.string.timeline_add_place_a11y, label, destination)
            TextButton(
                onClick = { onAdd(stepId, kind) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = spoken },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                Text("+ $label", color = Color(0xFF17333F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MoveRow(row: TimelineRow.Move, onOpen: () -> Unit) {
    val label = stepTypeLabel(row.step.type)
    val distance = row.km?.let { stringResource(R.string.timeline_km, it) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timeline_open, placeName(row.step)), onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(stepTypeIcon(row.step.type), contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Text(
            listOfNotNull(label, row.step.place.takeIf { it.isNotBlank() }, distance).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.padding(start = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val Ink = Color(0xFF111111)
private val InkSoft = Color(0xFF3A4A52)

@Composable
private fun nightsButtonColors() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
    containerColor = Color.White,
    contentColor = Ink,
    disabledContainerColor = Color(0xFFE6EBEE),
    disabledContentColor = Color(0xFF4F5B63),
)
