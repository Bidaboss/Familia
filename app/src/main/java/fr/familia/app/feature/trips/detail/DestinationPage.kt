package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.edit.TripDatesDialog
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.money.StepMoneyTile
import fr.familia.app.feature.trips.phrases.StepPhrasesTile
import fr.familia.app.feature.trips.place.placeKindIcon
import fr.familia.app.feature.trips.place.placeKindLabel
import fr.familia.app.feature.trips.word.PlaceAnecdoteTile
import fr.familia.app.feature.trips.word.WeatherTimeLine
import java.time.temporal.ChronoUnit

/** What the page of a destination opens besides the timeline (fiche F5). */
class DestinationLinks(
    /** « Modifier » of the destination: its form. */
    val onEdit: (stepId: String) -> Unit,
    /** « Tout savoir sur … »: « Le mot de l'étape », with safety, the article and around. */
    val onAllAbout: ((stepId: String) -> Unit)?,
    val onOpenPhrases: ((country: String) -> Unit)?,
    val onOpenMoney: ((String) -> Unit)?,
)

private val Ink = TripSheetColors.Ink
private val InkSoft = TripSheetColors.InkSoft
private val Deep = TripSheetColors.Deep
private val FieldBorder = Color(0xFFEDE3D5)
private val NumberYellow = Color(0xFFF1BF00)

/**
 * The page of a destination touched in « Le trajet » (fiche F5), in the sheet over the map zoomed on
 * it: the name and its nights, the weather and the time there, the arrival and the departure (the
 * departure moves its nights, règle 16), « Phrases utiles » and the currency abroad, the
 * accommodation, restaurants and spots, its photos and voice notes, the anecdote, and the bar of
 * the destinations to go from one to the next.
 */
@Composable
internal fun DestinationPage(
    container: AppContainer?,
    success: TripDetailUiState.Success,
    stayId: String,
    actions: TimelineActions,
    links: DestinationLinks,
    onSelect: (stayId: String) -> Unit,
    onBack: () -> Unit,
    inSheet: Boolean,
    /** « Ajout rapide » while this destination is the one of the day (règle 35): the camera round. */
    quickAdd: QuickAdd? = null,
) {
    val line = tripTimeline(success.trip, success.steps)
    val row = line.destinations.firstOrNull { it.step.id == stayId }
    if (row == null) {
        // Removed meanwhile: back to the trip.
        androidx.compose.runtime.LaunchedEffect(stayId) { onBack() }
        return
    }
    val name = placeName(row.step)
    TripFormScaffold(
        title = name,
        help = ScreenHelp.TripDetail,
        onBack = onBack,
        action = null,
        busy = false,
        onAction = {},
        inSheet = inSheet,
        subtitle = joinNames(success.participants),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DestinationCard(container, success, line, row, actions, links)
            StepPhrasesTile(container, row.step, links.onOpenPhrases)
            StepMoneyTile(container, row.step, links.onOpenMoney)
            Programme(row, actions)
            Souvenirs(row, name, actions)
            PlaceAnecdoteTile(row.step.place, container?.placeAnecdotes)
            links.onAllAbout?.let { open ->
                TextButton(
                    onClick = { open(row.step.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Deep),
                ) {
                    Text(stringResource(R.string.destination_all_about, name), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline)
                }
            }
        }
        if (quickAdd != null) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 84.dp)) { QuickAddButton(quickAdd) }
        }
        DestinationBar(line.destinations, stayId, onSelect, Modifier.align(Alignment.BottomCenter))
    }
}

/** The white card: name, nights, weather and time, arrival and departure (fiche F5). */
@Composable
private fun DestinationCard(
    container: AppContainer?,
    success: TripDetailUiState.Success,
    line: TripTimeline,
    row: TimelineRow.Destination,
    actions: TimelineActions,
    links: DestinationLinks,
) {
    val name = placeName(row.step)
    var picking by rememberSaveable(row.step.id) { mutableStateOf(false) }
    var blocked by rememberSaveable(row.step.id) { mutableStateOf<NightsBlock?>(null) }
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                fontFamily = Fredoka,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (success.isParticipant) {
                IconButton(onClick = { links.onEdit(row.step.id) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.destination_edit, name), tint = InkSoft)
                }
            }
        }
        Text(pluralStringResource(R.plurals.destination_nights, row.nights, row.nights), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkSoft)
        val lat = row.step.lat
        val lng = row.step.lng
        if (lat != null && lng != null) WeatherTimeLine(lat, lng, container?.placeWeather, InkSoft)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DayField(stringResource(R.string.destination_arrival), frenchDate(row.start, "EEE d MMMM"), onClick = null, Modifier.weight(1f))
            val canChange = actions.onNights != null
            val departure = frenchDate(row.end, "EEE d MMMM")
            DayField(
                stringResource(R.string.destination_departure),
                departure,
                onClick = if (canChange) ({ picking = true }) else null,
                Modifier.weight(1f),
                described = stringResource(R.string.destination_departure_a11y, departure),
            )
        }
        val first = line.destinations.firstOrNull()?.step?.id == row.step.id
        Text(
            stringResource(if (first) R.string.destination_arrival_first else R.string.destination_arrival_follows),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = InkSoft,
        )
        blocked?.let { why ->
            Text(
                stringResource(if (why == NightsBlock.OneNight) R.string.timeline_one_night else R.string.timeline_at_return),
                fontSize = 13.sp,
                color = InkSoft,
            )
        }
    }
    val change = actions.onNights
    if (picking && change != null) {
        val stay = row.step
        TripDatesDialog(
            start = row.start,
            end = row.end,
            onSave = { _, end ->
                picking = false
                val delta = ChronoUnit.DAYS.between(row.end, end).toInt()
                blocked = if (delta == 0) null else (change(stay.id, delta) as? NightsChange.Blocked)?.why
            },
            onDismiss = { picking = false },
            fixedStart = true,
            latest = latestDeparture(success.trip, success.steps, stay),
            title = stringResource(R.string.destination_departure_title, name),
        )
    }
}

/** « ARRIVÉE » over a white field of 56 dp; the departure opens the calendar for who goes. */
@Composable
private fun DayField(label: String, day: String, onClick: (() -> Unit)?, modifier: Modifier, described: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = InkSoft, letterSpacing = 0.6.sp)
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(Color.White, shape)
                .border(2.dp, FieldBorder, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick).semantics { if (described != null) contentDescription = described }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(day, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The programme of the destination laid out by day (récit 122, DESIGN-GAZETTE-1 A): its accommodation,
 * each day of the stay with « + Ajouter » for who goes, then « À caser ».
 */
@Composable
private fun Programme(row: TimelineRow.Destination, actions: TimelineActions) {
    if (!hasProgramme(row, actions) && actions.onAddPlace == null) return
    DayProgramme(row, actions, full = true)
}

/** The photos of the destination and its voice notes, as under its card in « Le trajet ». */
@Composable
private fun Souvenirs(row: TimelineRow.Destination, name: String, actions: TimelineActions) {
    val photos = actions.photos[row.step.id].orEmpty()
    val notes = actions.voiceNotes[row.step.id].orEmpty()
    if (photos.isEmpty() && notes.isEmpty() && actions.onAddPhoto == null) return
    WhiteCard {
        StepPhotoStrip(photos, name, actions.onOpenPhoto, actions.onAddPhoto?.let { add -> { add(row.step.id) } })
        notes.forEach { note -> VoiceNoteRow(note, actions) }
    }
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, contentColor = Ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/**
 * The floating bar of the destinations (fiche F5): a white pill, the destination shown in deep blue
 * with its number in yellow; a touch goes to another one. Each is 56 dp high.
 */
@Composable
private fun DestinationBar(destinations: List<TimelineRow.Destination>, stayId: String, onSelect: (String) -> Unit, modifier: Modifier) {
    if (destinations.size < 2 && destinations.firstOrNull()?.step?.id == stayId) return
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(32.dp))
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                val chosen = destination.step.id == stayId
                val name = placeName(destination.step)
                val described = stringResource(R.string.destination_bar_a11y, destination.number, name)
                Row(
                    Modifier
                        .heightIn(min = 56.dp)
                        .background(if (chosen) Deep else Color.Transparent, RoundedCornerShape(28.dp))
                        .selectable(selected = chosen, role = Role.Tab, onClick = { onSelect(destination.step.id) })
                        .semantics(mergeDescendants = true) { contentDescription = described }
                        .padding(start = 6.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(32.dp).background(NumberYellow, CircleShape), contentAlignment = Alignment.Center) {
                        Text(destination.number.toString(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    }
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (chosen) Color.White else Ink, maxLines = 1)
                }
            }
        }
    }
}
