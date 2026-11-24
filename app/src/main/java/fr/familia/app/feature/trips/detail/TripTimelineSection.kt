package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.PlaceCover
import fr.familia.app.core.place.PlacePhotos
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
    /** The photos of the places (Wikimedia), for the picture of each destination card (F2 §1). */
    val placePhotos: PlacePhotos? = null,
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
    // Fiche F2 §1: no card and no title on screen, the blocks hang on a thin grey-blue line through the rounds.
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val x = ROUND_SIZE.toPx() / 2
                drawLine(Thread, Offset(x, ROUND_SIZE.toPx() / 2), Offset(x, size.height - ROUND_SIZE.toPx() / 2), strokeWidth = 2.dp.toPx())
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = stringResource(R.string.timeline_title)
        EndRow(
            text = stringResource(R.string.timeline_start),
            day = trip.startDate,
            icon = Icons.Outlined.Home,
            round = Deep,
            // « Le trajet » stays a heading for TalkBack (F2 point 4), on the first block.
            headingText = title,
            trailing = { PlannedPill(line.planned, line.total) },
        )
        if (line.missing > 0 && line.destinations.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.timeline_missing, line.missing, line.missing),
                style = MaterialTheme.typography.bodyMedium,
                color = TripSheetColors.OnWarning,
                // E-57: a band of 40 dp right under the departure.
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(TripSheetColors.Warning).padding(horizontal = 12.dp, vertical = 10.dp),
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
            Text(
                stringResource(R.string.timeline_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
            )
        }
        val spots = remember(trip, line) { insertSpots(trip, line.rows) }
        val hasDestination = line.destinations.isNotEmpty()
        // E-57: one row of 56 dp per « + », the transport after it on the same row (no transport on a row of its own).
        if (line.rows.firstOrNull() !is TimelineRow.Move) spots.firstOrNull()?.let { InsertPoint(it, actions, hasDestination) }
        line.rows.forEachIndexed { index, row ->
            when (row) {
                is TimelineRow.Destination -> DestinationRow(
                    trip = trip,
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
                is TimelineRow.Move -> PlusAndMove(spots.getOrNull(index), row, actions, hasDestination)
            }
            if (insertAfter(line.rows, index)) spots.getOrNull(index + 1)?.let { InsertPoint(it, actions, hasDestination) }
        }
        if (actions.withoutStep > 0) {
            val label = pluralStringResource(R.plurals.timeline_photos_without_step, actions.withoutStep, actions.withoutStep)
            TextButton(onClick = actions.onSeePhotos, modifier = Modifier.padding(start = ROUND_SIZE).heightIn(min = 56.dp)) {
                Text("$label ›", color = Deep, fontWeight = FontWeight.Bold)
            }
        }
        EndRow(text = stringResource(R.string.timeline_end), day = trip.endDate, icon = Icons.Outlined.Flag, round = EndRound, trailing = {})
    }
}

/** « Bordeaux » out of « Bordeaux, Gironde, France ». */
internal fun placeName(step: TripStep): String = step.place.substringAfterLast('→').substringBefore(',').trim().ifEmpty { step.place }

private fun shortDay(day: LocalDate): String = frenchDate(day, "EEE d MMM")

/** The menu of a « + » of the timeline (F2 §2): where it adds, and who may add. Shared with the dark « + » of the sheet. */
@Composable
fun TimelineAddMenu(expanded: Boolean, onDismiss: () -> Unit, spot: InsertSpot, actions: TimelineActions, hasDestination: Boolean) {
    val insert = actions.onInsertStep ?: return
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MenuBackground,
    ) {
        InsertItem(stringResource(R.string.timeline_add_destination), Icons.Outlined.Add) { onDismiss(); insert(StepAddKind.Destination, spot.day) }
        InsertItem(stringResource(R.string.timeline_add_transport), Icons.Outlined.DirectionsCar) { onDismiss(); insert(StepAddKind.Transport, spot.day) }
        val addPlace = actions.onAddPlace
        val destination = spot.destinationId
        // F2 point 6: an accommodation, a spot or a restaurant belongs to a destination; none yet, not offered.
        if (addPlace != null && destination != null && hasDestination) {
            listOf(
                TripPlaceKind.Lodging to R.string.timeline_add_lodging,
                TripPlaceKind.Spot to R.string.timeline_add_spot,
                TripPlaceKind.Restaurant to R.string.timeline_add_restaurant,
            ).forEach { (kind, label) ->
                InsertItem(stringResource(label), placeKindIcon(kind)) { onDismiss(); addPlace(destination, kind) }
            }
        }
        actions.onEnrich?.let { enrich ->
            InsertItem(stringResource(R.string.timeline_add_enrich), Icons.Outlined.AutoAwesome) { onDismiss(); enrich() }
        }
    }
}

/** « Le voyage commence » and « Le voyage se termine » (F2 §1): a 48 dp round on the line, the text, the day in capitals. */
@Composable
private fun EndRow(text: String, day: LocalDate, icon: ImageVector, round: Color, trailing: @Composable () -> Unit, headingText: String? = null) {
    val spokenDay = frenchDate(day, "EEEE d MMMM yyyy")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(headingText, text, spokenDay).joinToString(", ")
                    if (headingText != null) heading()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ROUND_SIZE).clip(CircleShape).background(round), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    frenchDate(day, "EEE d MMMM yyyy").uppercase(java.util.Locale.FRANCE),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = InkSoft,
                )
            }
        }
        trailing()
    }
}

/** « 7/7 nuits planifiées » (F2 §1): a white pill with a small ring of progress. */
@Composable
private fun PlannedPill(planned: Int, total: Int) {
    val description = stringResource(R.string.timeline_planned_a11y, planned, total)
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { if (total == 0) 1f else (planned.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.size(22.dp),
            strokeWidth = 3.dp,
            color = Deep,
            trackColor = TripSheetColors.Track,
        )
        Text(
            stringResource(R.string.timeline_planned, planned, total),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Ink,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * A destination (F2 §1): a white card 76 dp high, rounded 32 — the photo of the place with its
 * number (the one of the round on the map, règle 7), the name and the dates, then − nuitées +.
 * Under it, its places, photos and voice notes in compact rows (F2 point 1).
 */
@Composable
private fun DestinationRow(trip: Trip, row: TimelineRow.Destination, plusBlocked: Boolean, actions: TimelineActions, blocked: NightsBlock?, onChange: (Int) -> Unit) {
    val name = placeName(row.step)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(32.dp)).background(Color.White).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp)) {
                PlaceCover(
                    place = name,
                    point = row.step.lat?.let { lat -> row.step.lng?.let { GeoPoint(lat, it) } },
                    landscape = trip.landscape,
                    photos = actions.placePhotos,
                    showCredit = false,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
                )
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(Deep).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(row.number.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timeline_open, name)) { actions.onOpen(row.step.id) }
                    .heightIn(min = 56.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("$name ›", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.timeline_dates, shortDay(row.start), shortDay(row.end)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                    fontWeight = FontWeight.Bold,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val canChange = actions.onNights != null
            if (canChange) {
                NightsButton("−", stringResource(R.string.timeline_less_a11y, name), enabled = row.nights > 1) { onChange(-1) }
            }
            Column(
                Modifier.widthIn(min = 52.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(row.nights.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.Black, color = Ink)
                Text(
                    pluralStringResource(R.plurals.timeline_nights_word, row.nights),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = InkSoft,
                )
            }
            if (canChange) {
                NightsButton("+", stringResource(R.string.timeline_more_a11y, name), enabled = !plusBlocked) { onChange(1) }
            }
        }
        if (actions.onNights != null && (plusBlocked || blocked != null)) {
            Text(
                stringResource(if (blocked == NightsBlock.OneNight) R.string.timeline_one_night else R.string.timeline_at_return),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(start = ROUND_SIZE + 8.dp),
            )
        }
        val places = actions.places[row.step.id].orEmpty()
        val photos = actions.photos[row.step.id].orEmpty()
        val notes = actions.voiceNotes[row.step.id].orEmpty()
        // E-50: no card with only the add-photo icon; the photos are added from the page of the destination.
        if (places.isNotEmpty() || photos.isNotEmpty() || notes.isNotEmpty()) {
            Column(
                Modifier.padding(start = ROUND_SIZE + 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                DestinationProgramme(places, actions)
                if (photos.isNotEmpty()) StepPhotoStrip(photos, name, actions.onOpenPhoto, actions.onAddPhoto?.let { add -> { add(row.step.id) } })
                notes.forEach { note -> VoiceNoteRow(note, actions) }
            }
        }
    }
}

/** − or + of the nights: a 56 × 56 dp zone (B1 of #593), the sign in dark ink, pale when it cannot change. */
@Composable
private fun NightsButton(sign: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (enabled) Ink else TripSheetColors.OnDisabled.copy(alpha = 0.5f))
    }
}

/**
 * The photos of a destination (règle 31): the first four, 56 dp each, each said by TalkBack and
 * opening the album on it; « + N » opens the album on the fifth; « Ajouter une photo » files the
 * photos chosen under this destination (récit 104, partie 2), for those who add photos.
 */
@Composable
internal fun StepPhotoStrip(photos: List<AlbumPhoto>, destination: String, onOpen: (String) -> Unit, onAdd: (() -> Unit)?) {
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
internal fun VoiceNoteRow(note: TripVoiceNote, actions: TimelineActions) {
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
 * opening its page (règle 19). They are added by the « + » after the destination (F2 §2).
 */
@Composable
internal fun DestinationProgramme(places: List<TripPlace>, actions: TimelineActions) {
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
}

/**
 * A « + » between two blocks of the timeline (DESIGN-99 A, F2 §1): a white round of 48 dp with a
 * cream border on the line, opening the menu of F2 §2 there. Nothing for who does not go.
 */
/**
 * E-57: whether a lone « + » follows row [index]. A transport has its « + » on its own row, before
 * it: after a transport, and before one, there is no other « + ».
 */
internal fun insertAfter(rows: List<TimelineRow>, index: Int): Boolean =
    rows.getOrNull(index) !is TimelineRow.Move && rows.getOrNull(index + 1) !is TimelineRow.Move

/** E-57: the « + » and, on the same row of 56 dp, the transport that follows it. */
@Composable
private fun PlusAndMove(spot: InsertSpot?, row: TimelineRow.Move, actions: TimelineActions, hasDestination: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (spot != null && actions.onInsertStep != null) InsertPoint(spot, actions, hasDestination) else Spacer(Modifier.width(ROUND_SIZE))
        MoveRow(row, onOpen = { actions.onOpen(row.step.id) }, Modifier.weight(1f))
    }
}

@Composable
private fun InsertPoint(spot: InsertSpot, actions: TimelineActions, hasDestination: Boolean) {
    if (actions.onInsertStep == null) return
    var open by remember { mutableStateOf(false) }
    val spoken = spot.after?.let { stringResource(R.string.timeline_insert_after_a11y, placeName(it)) } ?: stringResource(R.string.timeline_insert_start_a11y)
    Box {
        Box(
            Modifier
                .offset(x = (-4).dp)
                .size(56.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(ROUND_SIZE).clip(CircleShape).background(Color.White).border(2.dp, Cream, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
            }
        }
        TimelineAddMenu(open, { open = false }, spot, actions, hasDestination)
    }
}

@Composable
private fun InsertItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.ExtraBold, color = Ink) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

@Composable
private fun MoveRow(row: TimelineRow.Move, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = stepTypeLabel(row.step.type)
    val distance = row.km?.let { stringResource(R.string.timeline_km, it) }
    // B of #602: the touch zone is the Box of 56 dp; the white band of 48 dp (E-57) is drawn inside it.
    Box(
        modifier
            .padding(start = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timeline_open, placeName(row.step)), onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.55f))
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
}

private val Ink = TripSheetColors.Ink
private val InkSoft = TripSheetColors.InkSoft
private val Deep = TripSheetColors.Deep

/** F2 §1: the rounds of the timeline, its thin line, the border of a « + », the round of the arrival, the menu. */
private val ROUND_SIZE = 48.dp
private val Thread = Color(0xFFB8C7D0)
private val Cream = Color(0xFFEDE3D6)
private val EndRound = Color(0xFF9FB2BD)
private val MenuBackground = Color(0xFFF4F5F7)
