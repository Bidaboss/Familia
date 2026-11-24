package fr.familia.app.feature.trips.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import java.util.Locale
import androidx.compose.ui.unit.sp
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.carnet.flagOf
import fr.familia.app.core.model.TravelPlace
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.drawLandscape
import fr.familia.app.feature.trips.detail.palette
import fr.familia.app.core.place.PlacePhotos
import fr.familia.app.feature.trips.PlaceCover
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.map.GlobeHealth
import java.time.LocalDate

/** A part of a trip that the card of the next trip opens directly. */
enum class TripPart { Steps, ToBring, Expenses }

/**
 * The trips the member follows (récit 80, DESIGN-52 B): « À venir » shows the path to the next
 * trip, then the others; « Souvenirs » the past ones in a grid. The colors of the inside of a trip,
 * whatever the theme. Stateless.
 */
@Composable
fun TripsScreen(
    state: TripsUiState,
    onRetry: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onAddTrip: () -> Unit,
    /** Null when the list is a tab of the bar: nothing to go back to. */
    onBack: (() -> Unit)?,
    onOpenAlbums: (() -> Unit)? = null,
    /** Étapes, À prévoir, Comptes of the next trip; null: the card has no buttons. */
    onOpenPart: ((tripId: String, part: TripPart) -> Unit)? = null,
    /** Whether « Comptes » exists for this member (the switch, an adult). */
    expensesOn: Boolean = false,
    /** Public Mapbox token of this APK; blank: no globe, the drawn landscape stays (récit 99). */
    globeToken: String = "",
    /** The real photos of the places (règle 12); null: the satellite image, else the drawn landscape. */
    placePhotos: PlacePhotos? = null,
    /** « Carnet de baroudeur » (récit 107); null: no button. */
    onOpenCarnet: (() -> Unit)? = null,
    /** The destinations the member added by hand to the carnet (règle 38): in Souvenirs and a mark on the globe. */
    handAdded: List<TravelPlace> = emptyList(),
) {
    var souvenirs by rememberSaveable { mutableStateOf(false) }
    var globeStopped by rememberSaveable { mutableStateOf(false) }
    // The globe gave up (slow phone, no network, Mapbox error): the landscape from now on, and for 6 h on this phone.
    var globeFailed by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val success = state as? TripsUiState.Success
    val globeAllowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    val globeOn = globeToken.isNotBlank() && globeAllowed && !globeFailed && success?.isEmpty == false
    val markers = if (success == null || !globeOn) {
        emptyList()
    } else if (souvenirs) {
        success.past.mapNotNull { s -> s.place?.let { GlobeMarker(it, false, s.trip.id) } }
    } else {
        (success.ongoing + success.upcoming).mapNotNull { s -> s.place?.let { GlobeMarker(it, false, s.trip.id) } }
            .let { list -> list.mapIndexed { i, m -> if (i == 0) m.copy(highlighted = true) else m } }
    }
    // Opening a trip from the globe: the camera flies there for 2 s, then the trip opens; a second touch opens it at once.
    var flight by remember { mutableStateOf<GlobeFlight?>(null) }
    var flights by remember { mutableStateOf(0) }
    val still = remember { animationsOff(context) }
    androidx.compose.runtime.LaunchedEffect(flight?.id) {
        flight?.let {
            kotlinx.coroutines.delay(FLIGHT_MILLIS)
            flight = null
            onOpenTrip(it.tripId)
        }
    }
    val openTrip: (String) -> Unit = { id ->
        val place = success?.let { (it.ongoing + it.upcoming + it.past).firstOrNull { s -> s.trip.id == id }?.place }
        if (globeOn && place != null && !still && flight == null) {
            flights++
            flight = GlobeFlight(flights, id, place)
        } else {
            flight = null
            onOpenTrip(id)
        }
    }
    // Over the globe the top bar sits on the night sky.
    val dark = globeOn
    val skyColor = if (dark) Color.Transparent else TripPathColors.Sky
    Box(Modifier.fillMaxSize().then(if (dark) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0B1F45), Color(0xFF050B1A)))) else Modifier)) {
    if (globeOn) {
        TripsGlobe(
            token = globeToken,
            markers = markers,
            handAdded = handAdded.map { GeoPoint(it.lat, it.lng) },
            souvenirs = souvenirs,
            stopped = globeStopped,
            flight = flight,
            onFailed = { GlobeHealth.markFailed(context); globeFailed = true },
            onOpenTrip = { id -> openTrip(id) },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.TopCenter),
        )
    }
    CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
        Scaffold(
            containerColor = skyColor,
            contentColor = TripPathColors.Ink,
            topBar = {
                Column(Modifier.background(skyColor).statusBarsPadding()) {
                    TripsTopBar(onBack, onDark = dark, onOpenCarnet = onOpenCarnet)
                    if (success != null && !success.isEmpty) {
                        TripsSwitch(souvenirs, success.past.size + handAdded.size, onChoose = { souvenirs = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            },
            bottomBar = {
                if (success?.canCreate == true) StickyActionBar(text = "+ " + stringResource(R.string.trips_add), onClick = onAddTrip)
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
              if (globeOn) {
                Box(Modifier.weight(0.4f).fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { globeStopped = !globeStopped },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).heightIn(min = 56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    ) { Text(stringResource(if (globeStopped) R.string.globe_list_resume else R.string.globe_list_stop)) }
                }
              }
              Box(
                Modifier.weight(if (globeOn) 0.6f else 1f).fillMaxWidth()
                    .then(if (globeOn) Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(TripPathColors.Sky) else Modifier),
              ) {
                when (state) {
                    TripsUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
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
                    is TripsUiState.Success -> when {
                        state.isEmpty && !state.canCreate -> EmptyState(
                            icon = Icons.Outlined.CardTravel,
                            title = stringResource(R.string.trips_empty_title),
                            body = stringResource(R.string.trips_empty_body_child),
                            action = onOpenAlbums?.let { open -> { ClayLink(stringResource(R.string.trips_empty_albums), onClick = open) } },
                            mascot = true,
                        )
                        souvenirs -> Souvenirs(state, openTrip, handAdded, onOpenCarnet)
                        else -> Coming(state, openTrip, onOpenPart, expensesOn, withLandscape = !globeOn, photos = placePhotos, token = globeToken)
                    }
                }
              }
            }
        }
    }
    }
}

/** The round « Retour », « Voyages » (or [title]) in the title font, the round « ? ». */
@Composable
internal fun TripsTopBar(
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.trips_title),
    help: ScreenHelp = ScreenHelp.Trips,
    onDark: Boolean = false,
    onOpenCarnet: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink) { BackButton(onClick = onBack) }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (onDark) Color.White else TripPathColors.Ink,
            modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
        )
        // « Carnet de baroudeur » (récit 107): the round button left of « ? », as in the prototype.
        if (onOpenCarnet != null) {
            Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink) {
                IconButton(onClick = onOpenCarnet, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.BarChart, contentDescription = stringResource(R.string.carnet_open))
                }
            }
        }
        Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink) { HelpButton(help) }
    }
}

/** « À venir | Souvenirs (3) », as the switch of the Accueil: the chosen one dark. */
@Composable
private fun TripsSwitch(souvenirs: Boolean, pastCount: Int, onChoose: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false, true).forEach { past ->
            val chosen = souvenirs == past
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (chosen) TripPathColors.Ink else Color.White)
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onChoose(past) })
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (past) R.string.trips_souvenirs else R.string.trips_upcoming),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (chosen) Color.White else TripPathColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (past && pastCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$pastCount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TripPathColors.Ink,
                        modifier = Modifier.clip(CircleShape).background(TripPathColors.Sun).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** « À venir »: the landscape to the next trip and its card on the dune, then the others on the sand. */
@Composable
private fun Coming(
    state: TripsUiState.Success,
    onOpenTrip: (String) -> Unit,
    onOpenPart: ((String, TripPart) -> Unit)?,
    expensesOn: Boolean,
    withLandscape: Boolean = true,
    photos: PlacePhotos? = null,
    token: String = "",
) {
    val plan = tripsPathPlan(state.ongoing, state.upcoming)
    val hero = plan.hero
    val goal = when {
        hero == null -> LandscapeGoal.Nothing(stringResource(R.string.trips_next_adventure))
        plan.heroOngoing -> {
            val days = tripLength(hero.trip)
            val day = tripDayNumber(hero.trip, state.today)
            LandscapeGoal.Ongoing(day, days, stringResource(R.string.trips_day_of, day, days))
        }
        else -> LandscapeGoal.Next(waitLabel(hero.trip, state.today))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        val landscape = hero?.trip?.landscape ?: TripLandscape.Dunes
        val ground = landscape.palette.near
        if (withLandscape) item(key = "landscape") { TripsLandscape(goal, stringResource(R.string.day_today), landscape = landscape) }
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().background(ground).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (hero != null) {
                    HeroCard(hero, plan.heroOngoing, state.today, onOpenTrip, onOpenPart, expensesOn, photos, token)
                } else {
                    Text(
                        stringResource(if (state.canCreate) R.string.trips_none_coming else R.string.trips_none_coming_child),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TripPathColors.Ink,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp),
                    )
                }
            }
        }
        rows(R.string.trips_now, "now", plan.now, state.today, ground, onOpenTrip)
        rows(R.string.trips_then, "then", plan.next, state.today, ground, onOpenTrip)
        item(key = "sand") { Spacer(Modifier.fillMaxWidth().heightIn(min = 24.dp).background(ground)) }
    }
}

private fun LazyListScope.rows(title: Int, key: String, trips: List<TripSummary>, today: LocalDate, ground: Color, onOpenTrip: (String) -> Unit) {
    if (trips.isEmpty()) return
    item(key = "title-$key") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TripPathColors.Ink,
            modifier = Modifier.fillMaxWidth().background(ground).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        )
    }
    items(trips, key = { "$key-${it.trip.id}" }) { summary ->
        Box(Modifier.fillMaxWidth().background(ground).padding(horizontal = 16.dp, vertical = 6.dp)) {
            TripRow(summary, today, onClick = { onOpenTrip(summary.trip.id) })
        }
    }
}

/** « Dans 142 jours », « Demain », « Aujourd'hui ». */
@Composable
private fun waitLabel(trip: Trip, today: LocalDate): String = when (val days = daysUntil(trip, today)) {
    0 -> stringResource(R.string.day_today)
    1 -> stringResource(R.string.day_tomorrow)
    else -> pluralStringResource(R.plurals.trips_in_days, days, days)
}

/** « Du dim. 11 au ven. 16 avril 2027 · 6 jours ». */
@Composable
private fun longDates(trip: Trip): String {
    val (from, to) = tripRangeEnds(trip, weekday = true)
    val days = tripLength(trip)
    val range = if (trip.startDate == trip.endDate) stringResource(R.string.trips_one_day_on, to) else stringResource(R.string.trip_dates, from, to)
    return stringResource(R.string.trips_dates_and_length, range, pluralStringResource(R.plurals.trips_days, days, days))
}

/** What TalkBack reads of a trip: « La Palmyre, du dimanche 11 au vendredi 16 avril 2027, dans 142 jours, avec Inès et Hugo ». */
@Composable
private fun tripDescription(summary: TripSummary, extra: String?): String {
    val trip = summary.trip
    val from = fr.familia.app.core.ui.frenchDate(trip.startDate, "EEEE d MMMM yyyy")
    val to = fr.familia.app.core.ui.frenchDate(trip.endDate, "EEEE d MMMM yyyy")
    val parts = listOfNotNull(
        trip.title,
        if (trip.startDate == trip.endDate) from else stringResource(R.string.trips_dates_spoken, from, to),
        extra,
        summary.participants.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.trip_with, joinNames(it)).lowercase() },
    )
    return parts.joinToString(", ")
}

@Composable
private fun HeroCard(
    summary: TripSummary,
    ongoing: Boolean,
    today: LocalDate,
    onOpenTrip: (String) -> Unit,
    onOpenPart: ((String, TripPart) -> Unit)?,
    expensesOn: Boolean,
    photos: PlacePhotos? = null,
    token: String = "",
) {
    val trip = summary.trip
    val wait = if (ongoing) stringResource(R.string.trips_day_of, tripDayNumber(trip, today), tripLength(trip)) else waitLabel(trip, today)
    val description = tripDescription(summary, wait.lowercase())
    val openLabel = stringResource(R.string.trips_open_action)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The photo of the place where the trip goes (règles 10, 12), with its credit; no place, no image.
        if (summary.place != null && (photos != null || token.isNotBlank())) {
            PlaceCover(
                place = summary.placeName,
                point = summary.place,
                landscape = trip.landscape,
                photos = photos,
                token = token,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)),
            )
        }
        // The text of the card opens the trip; the three buttons open one of its parts.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = { onOpenTrip(trip.id) })
                .clearAndSetSemantics { contentDescription = "$description. $openLabel" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (ongoing) R.string.trips_now_label else R.string.trips_next_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TripPathColors.InkSoft,
            )
            Text(trip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = TripPathColors.Ink)
            Text(longDates(trip), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.InkSoft)
            Faces(summary.people, summary.participants)
        }
        if (onOpenPart != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartButton(stringResource(R.string.trips_part_steps), Modifier.weight(1f)) { onOpenPart(trip.id, TripPart.Steps) }
                PartButton(stringResource(R.string.trips_part_to_bring), Modifier.weight(1f)) { onOpenPart(trip.id, TripPart.ToBring) }
                if (expensesOn && summary.isParticipant) {
                    PartButton(stringResource(R.string.trips_part_expenses), Modifier.weight(1f)) { onOpenPart(trip.id, TripPart.Expenses) }
                }
            }
        }
    }
}

@Composable
private fun PartButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = TripPathColors.Button, contentColor = TripPathColors.Ink),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Up to four faces overlapping, then the first names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Faces(people: List<Member>, names: List<String>) {
    if (names.isEmpty()) return
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (people.isNotEmpty()) {
            val shown = people.take(4)
            Box(Modifier.width((34 + (shown.size - 1) * 24).dp).align(Alignment.CenterVertically)) {
                shown.forEachIndexed { i, member ->
                    MemberAvatar(
                        member,
                        size = 34.dp,
                        modifier = Modifier.offset(x = (i * 24).dp).clip(CircleShape).background(Color.White).padding(2.dp),
                    )
                }
            }
        }
        Text(
            joinNames(names),
            style = MaterialTheme.typography.bodyMedium,
            color = TripPathColors.InkSoft,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/** A line of « Ensuite » or « En ce moment »: the small landscape, the title, the dates, the faces and when. */
@Composable
private fun TripRow(summary: TripSummary, today: LocalDate, onClick: () -> Unit) {
    val trip = summary.trip
    val (from, to) = tripRangeEnds(trip, weekday = false)
    val dates = if (trip.startDate == trip.endDate) to else stringResource(R.string.trips_dates_short, from, to)
    val ongoing = !trip.startDate.isAfter(today)
    val pill = if (ongoing) stringResource(R.string.trips_day_of, tripDayNumber(trip, today), tripLength(trip)) else waitLabel(trip, today)
    val description = tripDescription(summary, pill.lowercase())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniLandscape(trip.landscape, Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TripPathColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dates, style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
            Text(
                pill,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TripPathColors.Ink,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(TripPathColors.Sun).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TripPathColors.InkSoft)
    }
}

/** The landscape of the trip in small (récit 81). [pale]: for a past trip, which has no cover. */
@Composable
internal fun MiniLandscape(landscape: TripLandscape, modifier: Modifier, pale: Boolean = false) {
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(16.dp)).alpha(if (pale) 0.55f else 1f)) {
        drawLandscape(landscape)
    }
}

/** « Souvenirs »: the past trips, most recent first, two by row. */
@Composable
private fun Souvenirs(state: TripsUiState.Success, onOpenTrip: (String) -> Unit, handAdded: List<TravelPlace> = emptyList(), onOpenCarnet: (() -> Unit)? = null) {
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    // The trips and the hand-added destinations (règle 38) together, the most recent first.
    val cards: List<Any> = (state.past.map { it.trip.startDate to it } + handAdded.map { it.date to it }).sortedByDescending { it.first }.map { it.second }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Text(
                stringResource(if (cards.isEmpty()) R.string.trips_souvenirs_empty else R.string.trips_souvenirs_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TripPathColors.InkSoft,
            )
        }
        items(cards.chunked(columns), key = { row -> row.first().let { if (it is TripSummary) "past-" + it.trip.id else "hand-" + (it as TravelPlace).id } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card ->
                    when (card) {
                        is TripSummary -> SouvenirCard(card, Modifier.weight(1f)) { onOpenTrip(card.trip.id) }
                        is TravelPlace -> HandAddedCard(card, Modifier.weight(1f), onOpenCarnet)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SouvenirCard(summary: TripSummary, modifier: Modifier, onClick: () -> Unit) {
    val trip = summary.trip
    val days = tripLength(trip)
    val line = stringResource(R.string.trips_dates_and_length, tripMonthLabel(trip), pluralStringResource(R.plurals.trips_days, days, days))
    val description = tripDescription(summary, null)
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        MiniLandscape(trip.landscape, Modifier.fillMaxWidth().aspectRatio(1.45f), pale = true)
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TripPathColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
        }
    }
}

/**
 * A destination added by hand to the carnet (récit 107, règle 38): no photo, no path, so its flag
 * on a sand band; a touch opens the carnet.
 */
@Composable
private fun HandAddedCard(place: TravelPlace, modifier: Modifier, onOpen: (() -> Unit)?) {
    val line = stringResource(
        R.string.trips_dates_and_length,
        place.date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.titlecase(Locale.FRANCE) },
        pluralStringResource(R.plurals.trips_days, place.days, place.days),
    )
    val added = stringResource(R.string.carnet_hand_added)
    val description = "${place.name}, $line, $added"
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.45f).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF7D9C0), Color(0xFFEAD9C4)))),
            contentAlignment = Alignment.Center,
        ) { Text(place.countryCode?.let(::flagOf).orEmpty(), fontSize = 40.sp) }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TripPathColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
            Text(added, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TripPathColors.InkSoft)
        }
    }
}

/** Fictional trips for previews and screenshots. */
internal fun previewTrips(): TripsUiState.Success {
    fun trip(id: String, title: String, start: LocalDate, end: LocalDate) =
        Trip(id, title, start, end, listOf("a", "b"), listOf("a", "b", "c"), "a", "Camille")
    return TripsUiState.Success(
        ongoing = emptyList(),
        upcoming = listOf(
            TripSummary(trip("2", "La Palmyre", LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 16)), listOf("Camille", "Inès", "Noé"), isParticipant = true),
            TripSummary(trip("4", "Annecy", LocalDate.of(2027, 7, 3), LocalDate.of(2027, 7, 10)), listOf("Inès", "Noé")),
        ),
        past = listOf(TripSummary(trip("3", "Ski à Morzine", LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 20)), listOf("Inès"))),
        today = LocalDate.of(2026, 11, 20),
    )
}

@FamiliaPreviews
@Composable
private fun TripsScreenPreview() {
    FamiliaTheme { Surface { TripsScreen(previewTrips(), {}, {}, {}, {}, onOpenPart = { _, _ -> }, expensesOn = true) } }
}
