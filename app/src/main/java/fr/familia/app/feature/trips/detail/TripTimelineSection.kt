package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import fr.familia.app.core.data.TripVoiceNote
import fr.familia.app.R
import fr.familia.app.core.model.Trip
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.place.placeKindIcon
import fr.familia.app.feature.trips.place.placeKindLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.voice.voiceDuration
import fr.familia.app.feature.trips.stepTypeLabel
import fr.familia.app.feature.trips.step.StepAddKind
import java.time.LocalDate
import java.time.LocalTime
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
    /** Null for who does not go: no « + » between the elements (DESIGN-99 A). A destination or a transport from [day]. */
    val onInsertStep: ((kind: StepAddKind, day: LocalDate) -> Unit)? = null,
    /** « Enrichir avec une IA » in the menu of a « + »; null when it is not offered. */
    val onEnrich: (() -> Unit)? = null,
    /** The photos of each destination (récit 104, règle 31), from [stepPhotos]; and the album opened on one of them. */
    val photos: Map<String, List<AlbumPhoto>> = emptyMap(),
    val onOpenPhoto: (photoId: String) -> Unit = {},
    /** The photos that fall in no destination, « Sans étape », and the album they are in. */
    val withoutStep: Int = 0,
    val onSeePhotos: () -> Unit = {},
    /** Null for who adds no photo; else the photos chosen go under the destination [stepId] (récit 104, partie 2). */
    val onAddPhoto: ((stepId: String) -> Unit)? = null,
    /** The voice notes of each destination (récit 104, partie 3b), oldest first; the one playing. */
    val voiceNotes: Map<String, List<TripVoiceNote>> = emptyMap(),
    val playingVoiceId: String? = null,
    val onPlayVoice: (TripVoiceNote) -> Unit = {},
    /** Null for a note this member may not remove. */
    val canDeleteVoice: (TripVoiceNote) -> Boolean = { false },
    val onDeleteVoice: (TripVoiceNote) -> Unit = {},
)

/**
 * Where a « + » of « Le trajet » adds (DESIGN-99 A): after « Le voyage commence » (index 0) and
 * after each element. [day]: the day the new element starts; [destinationId]: the destination
 * its accommodation, restaurant or spot goes under (the one before, else the next, else none).
 */
data class InsertSpot(val day: LocalDate, val destinationId: String?, val after: TripStep?)

internal fun insertSpots(trip: Trip, rows: List<TimelineRow>): List<InsertSpot> {
    val firstDestination = rows.firstOrNull { it is TimelineRow.Destination }?.step?.id
    var lastDestination: String? = null
    val spots = mutableListOf(InsertSpot(trip.startDate, firstDestination, null))
    rows.forEach { row ->
        val day = when (row) {
            is TimelineRow.Destination -> row.end
            is TimelineRow.Move -> row.step.arriveDate ?: row.step.departDate
        }.coerceIn(trip.startDate, trip.endDate)
        if (row is TimelineRow.Destination) lastDestination = row.step.id
        val next = rows.dropWhile { it !== row }.drop(1).firstOrNull { it is TimelineRow.Destination }?.step?.id
        spots += InsertSpot(day, lastDestination ?: next, row.step)
    }
    return spots
}

/** The places of each destination, the accommodation first, then the restaurants and the spots, each by time then name. */
internal fun placesByStep(places: List<TripPlace>): Map<String, List<TripPlace>> =
    places.groupBy { it.stepId }.mapValues { (_, list) ->
        list.sortedWith(compareBy<TripPlace>({ it.kind.ordinal }, { it.arriveTime ?: LocalTime.MAX }, { it.name.lowercase() }))
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TripSheetColors.Card).padding(12.dp),
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
                        color = Deep,
                        trackColor = TripSheetColors.Track,
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
                color = TripSheetColors.OnWarning,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TripSheetColors.Warning).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        undo?.let { (name, before) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Deep).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timeline_changed, name), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.onUndo(before); undo = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.timeline_undo), color = TripSheetColors.Highlight, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (line.rows.isEmpty()) {
            Text(stringResource(R.string.timeline_empty), style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        val spots = remember(trip, line) { insertSpots(trip, line.rows) }
        spots.firstOrNull()?.let { InsertPoint(it, actions) }
        line.rows.forEachIndexed { index, row ->
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
            spots.getOrNull(index + 1)?.let { InsertPoint(it, actions) }
        }
        if (actions.withoutStep > 0) {
            val label = pluralStringResource(R.plurals.timeline_photos_without_step, actions.withoutStep, actions.withoutStep)
            TextButton(onClick = actions.onSeePhotos, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("$label ›", color = Deep, fontWeight = FontWeight.Bold)
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
        Box(Modifier.size(36.dp).clip(CircleShape).background(Deep), contentAlignment = Alignment.Center) {
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TripSheetColors.Panel).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The number is the one of the round on the map (règle 7).
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Ink).border(3.dp, Color.White, CircleShape),
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
                    contentPadding = PaddingValues(0.dp),
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
                    contentPadding = PaddingValues(0.dp),
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
        StepPhotoStrip(actions.photos[row.step.id].orEmpty(), name, actions.onOpenPhoto, actions.onAddPhoto?.let { add -> { add(row.step.id) } })
        actions.voiceNotes[row.step.id].orEmpty().forEach { note -> VoiceNoteRow(note, actions) }
    }
}

/**
 * The photos of a destination (règle 31): the first four, 56 dp each, each said by TalkBack and
 * opening the album on it; « + N » opens the album on the fifth; « Ajouter une photo » files the
 * photos chosen under this destination (récit 104, partie 2), for those who add photos.
 */
@Composable
private fun StepPhotoStrip(photos: List<AlbumPhoto>, destination: String, onOpen: (String) -> Unit, onAdd: (() -> Unit)?) {
    if (photos.isEmpty() && onAdd == null) return
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        photos.take(STRIP_PHOTOS).forEach { photo ->
            StorageImage(
                path = photo.thumbnailPath,
                contentDescription = stringResource(R.string.album_photo_by, photo.authorName),
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.album_photo_open)) { onOpen(photo.id) },
            )
        }
        if (photos.size > STRIP_PHOTOS) {
            val more = photos.size - STRIP_PHOTOS
            val spoken = pluralStringResource(R.plurals.timeline_photos_a11y, photos.size, photos.size, destination)
            Box(
                Modifier
                    .size(56.dp)
                    .clip(shape)
                    .clickable(role = Role.Button) { onOpen(photos[STRIP_PHOTOS].id) }
                    .semantics { contentDescription = spoken },
                contentAlignment = Alignment.Center,
            ) {
                Text("+ $more", color = Deep, fontWeight = FontWeight.Bold)
            }
        }
        if (onAdd != null) {
            val add = stringResource(R.string.timeline_photo_add, destination)
            Box(
                Modifier
                    .size(56.dp)
                    .clip(shape)
                    .border(1.dp, Deep.copy(alpha = 0.4f), shape)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = add },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = Deep)
            }
        }
    }
}

private const val STRIP_PHOTOS = 4

/** « Note vocale de Lou, 0:42 »: listen or stop (56 dp), and remove it for its author or who manages the trip. */
@Composable
private fun VoiceNoteRow(note: TripVoiceNote, actions: TimelineActions) {
    val playing = actions.playingVoiceId == note.id
    var confirm by rememberSaveable(note.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.onPlayVoice(note) }, modifier = Modifier.size(56.dp)) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.voice_stop_playing else R.string.voice_play),
                tint = Deep,
            )
        }
        Text(
            stringResource(R.string.voice_note_by, note.authorName, voiceDuration(note.seconds)),
            style = MaterialTheme.typography.bodyMedium,
            color = Deep,
            modifier = Modifier.weight(1f),
        )
        if (actions.canDeleteVoice(note)) {
            IconButton(onClick = { confirm = true }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.voice_delete), tint = InkSoft)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.voice_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; actions.onDeleteVoice(note) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.voice_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Under a destination, in smaller type (règle 18): its accommodation, restaurants and spots, each
 * opening its page (règle 19); then « + Hébergement », « + Restaurant », « + Spot » for those who go (règle 20).
 */
@OptIn(ExperimentalLayoutApi::class)
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
                val time = place.arriveTime?.let { " · " + formatTime(it) }.orEmpty()
                Text(kind + time, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
    val onAdd = actions.onAddPlace ?: return
    // The three « + » go to the next line rather than cut « Hébergement » on a small screen or in large text.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TripPlaceKind.entries.forEach { kind ->
            val label = placeKindLabel(kind)
            val spoken = stringResource(R.string.timeline_add_place_a11y, label, destination)
            TextButton(
                onClick = { onAdd(stepId, kind) },
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = spoken },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("+ $label", color = Deep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A « + » between two elements of « Le trajet » (DESIGN-99 A, prototype): its menu adds a
 * destination, a transport, an accommodation, a spot or a restaurant there, or opens
 * « Enrichir avec une IA ». Nothing for who does not go.
 */
@Composable
private fun InsertPoint(spot: InsertSpot, actions: TimelineActions) {
    val insert = actions.onInsertStep ?: return
    var open by remember { mutableStateOf(false) }
    val spoken = spot.after?.let { stringResource(R.string.timeline_insert_after_a11y, placeName(it)) } ?: stringResource(R.string.timeline_insert_start_a11y)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(TripSheetColors.Track), contentAlignment = Alignment.Center) {
                Text("+", color = Deep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                InsertItem(stringResource(R.string.trip_step_type_lodging), Icons.Outlined.Place) { open = false; insert(StepAddKind.Destination, spot.day) }
                InsertItem(stringResource(R.string.timeline_insert_transport), Icons.Outlined.DirectionsCar) { open = false; insert(StepAddKind.Transport, spot.day) }
                val addPlace = actions.onAddPlace
                val destination = spot.destinationId
                if (addPlace != null && destination != null) {
                    TripPlaceKind.entries.forEach { kind ->
                        InsertItem(placeKindLabel(kind), placeKindIcon(kind)) { open = false; addPlace(destination, kind) }
                    }
                }
                actions.onEnrich?.let { enrich ->
                    InsertItem(stringResource(R.string.enrich_title), Icons.Outlined.AutoAwesome) { open = false; enrich() }
                }
            }
        }
    }
}

@Composable
private fun InsertItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
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

private val Ink = TripSheetColors.Ink
private val InkSoft = TripSheetColors.InkSoft
private val Deep = TripSheetColors.Deep

@Composable
private fun nightsButtonColors() = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
    containerColor = Color.White,
    contentColor = Ink,
    disabledContainerColor = TripSheetColors.Disabled,
    disabledContentColor = TripSheetColors.OnDisabled,
)
