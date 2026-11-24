package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripCountdown
import fr.familia.app.feature.trips.headNames
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.list.tripLength
import fr.familia.app.feature.trips.list.tripRangeEnds

/** The dark ink and the soft ink of the prototype (fiche F1 §6). */
private val Ink = Color(0xFF1E1B18)
private val InkSoft = Color(0xFF5B524A)

/**
 * The top of the screen of a trip (fiche F1 §3): « Retour », the white pill with the name of the trip
 * and who goes, « Partager », « ? », then the bubble « Départ dans 252 nuits » (DESIGN-68 A: nights) and,
 * over the satellite map, the credit of the map. The round buttons keep 56 dp (the touch rule of the app).
 */
@Composable
internal fun TripHeader(
    trip: Trip,
    participants: List<String>,
    countdown: TripCountdown,
    onBack: () -> Unit,
    onEditTrip: (() -> Unit)?,
    onShare: (() -> Unit)?,
    showCredit: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhiteRound { BackButton(onClick = onBack) }
            TitlePill(trip.title, participants, onEditTrip, Modifier.weight(1f))
            if (onShare != null) WhiteRound { ShareButton(onClick = onShare) }
            WhiteRound { HelpButton(ScreenHelp.TripDetail) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            countdownBubbleText(countdown)?.let { text ->
                Box(
                    Modifier.height(36.dp).shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            if (showCredit) {
                // A dark shadow under the light text keeps it readable on any satellite tile (C4 of #588).
                Text(
                    stringResource(R.string.trip_map_credit),
                    fontSize = 10.sp,
                    color = Color(0xFFDBE7EF),
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 1f), blurRadius = 4f)),
                )
            }
        }
    }
}

/** « Départ dans 252 nuits », « Demain, c'est le départ », « C'est le jour J », « Jour 2 sur 6 »; nothing once over. */
@Composable
private fun countdownBubbleText(countdown: TripCountdown): String? = when (countdown) {
    is TripCountdown.NightsBefore -> pluralStringResource(R.plurals.trip_bubble_nights, countdown.nights, countdown.nights)
    TripCountdown.Tomorrow -> stringResource(R.string.trip_countdown_tomorrow)
    TripCountdown.DepartureDay -> stringResource(R.string.trip_countdown_today)
    is TripCountdown.During -> stringResource(R.string.trip_countdown_during, countdown.day, countdown.of)
    TripCountdown.Over -> null
}

@Composable
private fun WhiteRound(content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, contentColor = Ink, shadowElevation = 2.dp) { content() }
}

/** « Lisbonne ✎ » over « Camille, Inès et 1 autre » (E-4), in a white pill; it opens « Modifier le voyage ». */
@Composable
private fun TitlePill(title: String, participants: List<String>, onEditTrip: (() -> Unit)?, modifier: Modifier) {
    val (shown, others) = headNames(participants)
    val names = if (others == 0) joinNames(shown) else pluralStringResource(R.plurals.trip_people_more, others, shown.joinToString(", "), others)
    val everyone = joinNames(participants)
    val editLabel = stringResource(R.string.trip_edit_a11y)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.95f),
        contentColor = Ink,
        shadowElevation = 2.dp,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Column(
            Modifier
                .then(if (onEditTrip != null) Modifier.clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEditTrip) else Modifier)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    fontFamily = Fredoka,
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (onEditTrip != null) Icon(Icons.Outlined.Edit, contentDescription = null, tint = InkSoft, modifier = Modifier.size(16.dp))
            }
            if (participants.isNotEmpty()) {
                Text(
                    names,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = everyone },
                )
            }
        }
    }
}

/** « Du lun. 2 au lun. 9 août 2027 · 8 jours ✎ » (F1 §5.2): it opens « Modifier le voyage » for who may. */
@Composable
internal fun TripDatesRow(trip: Trip, onEditTrip: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (from, to) = tripRangeEnds(trip, weekday = true)
    val days = tripLength(trip)
    val range = if (trip.startDate == trip.endDate) stringResource(R.string.trips_one_day_on, to) else stringResource(R.string.trip_dates, from, to)
    val line = stringResource(R.string.trips_dates_and_length, range, pluralStringResource(R.plurals.trips_days, days, days))
    val editLabel = stringResource(R.string.trip_edit_a11y)
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onEditTrip != null) Modifier.clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEditTrip) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(line, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f, fill = false))
        if (onEditTrip != null) Icon(Icons.Outlined.Edit, contentDescription = null, tint = InkSoft, modifier = Modifier.size(22.dp))
    }
}

/** The rounds of those who go, side by side, then their names (F1 §5.4). */
@Composable
internal fun TripPeopleRow(people: List<Member>, names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val everyone = joinNames(names)
    Row(
        modifier.clearAndSetSemantics { contentDescription = everyone },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (people.isNotEmpty()) {
            Row {
                people.take(5).forEach { member ->
                    MemberAvatar(member, size = 36.dp, modifier = Modifier.border(2.dp, Color.White, CircleShape))
                }
            }
        }
        Text(everyone, fontSize = 15.sp, color = InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** « ✦ Enrichir le voyage avec une IA » (règle 30, F1 §5.5): a small peach pill, 56 dp to touch. */
@Composable
internal fun TripEnrichPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFDEFE6))
                .border(1.5.dp, Ink, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.trip_enrich_pill), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1)
        }
    }
}

/** A tool of the trip under « Le trajet » (F1 §5.6): a white bordered pill, 56 dp, with a [badge] count. */
internal data class TripTool(val label: String, val description: String, val onClick: () -> Unit, val badge: Int? = null)

/** The tools in two columns: Comptes, Valise, Documents… (F1 §5.6). */
@Composable
internal fun TripToolsGrid(tools: List<TripTool>, modifier: Modifier = Modifier) {
    if (tools.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tool -> ToolPill(tool, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolPill(tool: TripTool, modifier: Modifier) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(2.dp, Color(0xFFE5DCCF), RoundedCornerShape(28.dp))
            .clickable(role = Role.Button, onClick = tool.onClick)
            .semantics(mergeDescendants = true) { contentDescription = tool.description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(tool.label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF17333F), maxLines = 1, overflow = TextOverflow.Ellipsis)
        tool.badge?.takeIf { it > 0 }?.let { count ->
            Box(
                Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).background(Color(0xFF17333F)),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.coerceAtMost(99).toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}
