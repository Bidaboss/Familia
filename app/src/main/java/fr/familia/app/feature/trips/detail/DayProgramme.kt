package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.trips.place.DayChoiceSheet
import fr.familia.app.feature.trips.place.DayPlan
import fr.familia.app.feature.trips.place.LodgingNights
import fr.familia.app.feature.trips.place.dayPlan
import fr.familia.app.feature.trips.place.nightsLabel
import fr.familia.app.feature.trips.place.placeKindIcon
import fr.familia.app.feature.trips.place.placeKindLabel
import fr.familia.app.feature.trips.place.stayDayLabel
import fr.familia.app.feature.trips.place.stayDays

private val Ink = TripSheetColors.Ink
private val InkSoft = TripSheetColors.InkSoft

/** The cream tile of an accommodation (maquette A). */
private val LodgingCream = Color(0xFFFFF6E3)

/** « À caser »: pale yellow, dashed in a darker yellow, the chip in yellow with brown ink. */
private val ToPlaceYellow = Color(0xFFFFF1C2)
private val ToPlaceDash = Color(0xFFE0A526)
private val ChipYellow = Color(0xFFFFD970)
private val ChipInk = Color(0xFF4A3500)

/** The places of [row] laid out on its stay (récit 122, DESIGN-GAZETTE-1 A). */
internal fun planOf(row: TimelineRow.Destination, actions: TimelineActions): DayPlan =
    dayPlan(row.step.id, row.start, row.end, actions.places[row.step.id].orEmpty())

/**
 * A destination laid out by day (récit 122, maquette A): its accommodations in a cream tile with
 * their nights, then one section per day of the stay (« Lun. 12 avril · J2 ») with its restaurants
 * and spots by hour, then « À caser » in dashed yellow: the places without a day, each with
 * « Choisir le jour ». [full] (the page of the destination): every day, « Rien de prévu, journée
 * libre » and « + Ajouter » for who goes; otherwise (« Le trajet ») only the days with something.
 */
@Composable
internal fun DayProgramme(row: TimelineRow.Destination, actions: TimelineActions, full: Boolean) {
    val plan = planOf(row, actions)
    val name = placeName(row.step)
    var choosing by remember { mutableStateOf<TripPlace?>(null) }
    val add = actions.onAddPlace.takeIf { full }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plan.lodgings.forEach { LodgingTile(it, actions) }
        if (add != null) AddButton(stringResource(R.string.destination_add_a11y, placeKindLabel(TripPlaceKind.Lodging)), stringResource(R.string.day_add_lodging)) { add(row.step.id, TripPlaceKind.Lodging, null) }
        plan.days.forEach { day ->
            if (!full && day.places.isEmpty()) return@forEach
            DayHeader(stayDayLabel(day.date, day.number), add?.let { a -> { kind: TripPlaceKind -> a(row.step.id, kind, day.date) } })
            if (day.places.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(stringResource(R.string.day_free), style = MaterialTheme.typography.bodyMedium, color = InkSoft) }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    day.places.forEach { PlaceLine(it, actions, onChooseDay = null) }
                }
            }
        }
        if (plan.toPlace.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.day_to_place), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.day_to_place_hint), style = MaterialTheme.typography.labelMedium, color = InkSoft)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(ToPlaceYellow, cornerRadius = CornerRadius(20.dp.toPx()))
                        drawRoundRect(
                            ToPlaceDash,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                plan.toPlace.forEach { place -> PlaceLine(place, actions, onChooseDay = actions.onPlaceDay?.let { { choosing = place } }) }
            }
        }
    }
    val place = choosing
    val setDay = actions.onPlaceDay
    if (place != null && setDay != null) {
        DayChoiceSheet(
            name = place.name,
            destination = name,
            days = stayDays(row.start, row.end),
            chosen = place.day,
            onChoose = { day ->
                choosing = null
                setDay(place, day)
            },
            onDismiss = { choosing = null },
        )
    }
}

/** True when the destination has something to lay out by day. */
internal fun hasProgramme(row: TimelineRow.Destination, actions: TimelineActions): Boolean =
    actions.places[row.step.id].orEmpty().isNotEmpty()

@Composable
private fun LodgingTile(lodging: LodgingNights, actions: TimelineActions) {
    val place = lodging.place
    val nights = nightsLabel(lodging.checkIn, lodging.checkOut)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(LodgingCream)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.timeline_open, place.name)) { actions.onOpenPlace(place.id) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(placeKindIcon(TripPlaceKind.Lodging), contentDescription = null, tint = InkSoft, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(placeKindLabel(TripPlaceKind.Lodging) + " · " + nights, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = InkSoft)
    }
}

/** « Lun. 12 avril · J2 » with « + Ajouter » (a restaurant or a spot that day) for who goes. */
@Composable
private fun DayHeader(label: String, onAdd: ((TripPlaceKind) -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onAdd != null) {
            var open by remember { mutableStateOf(false) }
            Box {
                AddButton(stringResource(R.string.day_add_a11y, label), stringResource(R.string.destination_add)) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(24.dp)) {
                    listOf(TripPlaceKind.Restaurant, TripPlaceKind.Spot).forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(placeKindLabel(kind), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.ExtraBold, color = Ink) },
                            leadingIcon = { Icon(placeKindIcon(kind), contentDescription = null, tint = Ink) },
                            onClick = { open = false; onAdd(kind) },
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A white pill « + Ajouter », 56 dp high. */
@Composable
private fun AddButton(described: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = described }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A restaurant or a spot: its page on a tap; under « À caser », the chip « Choisir le jour ». */
@Composable
private fun PlaceLine(place: TripPlace, actions: TimelineActions, onChooseDay: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.timeline_open, place.name)) { actions.onOpenPlace(place.id) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(placeKindIcon(place.kind), contentDescription = null, tint = InkSoft, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = place.arriveTime?.let { " · " + formatTime(it) }.orEmpty()
                Text(placeKindLabel(place.kind) + time, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        if (onChooseDay != null) {
            val described = stringResource(R.string.day_choose_a11y, place.name)
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onChooseDay)
                    .semantics { contentDescription = described }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.day_choose),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = ChipInk,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(ChipYellow).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
