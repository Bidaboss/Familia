package fr.familia.app.feature.trips.list

import fr.familia.app.feature.trips.detail.SheetHandle
import fr.familia.app.feature.trips.map.backdropSheetShare
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import fr.familia.app.core.power.BatterySavingBanner
import fr.familia.app.core.power.rememberBatterySaving
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
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
    // Règle 46: no globe while the battery is saved (under 20 % unplugged, or Android's saver).
    val saving = rememberBatterySaving()
    val globeOn = globeToken.isNotBlank() && globeAllowed && !globeFailed && !saving && success?.isEmpty == false
    val markers = if (success == null || !globeOn) {
        emptyList()
    } else if (souvenirs) {
        success.past.mapNotNull { s -> s.place?.let { GlobeMarker(it, false, s.trip.id, s.placeName.orEmpty()) } }
    } else {
        (success.ongoing + success.upcoming).mapNotNull { s -> s.place?.let { GlobeMarker(it, false, s.trip.id, s.placeName.orEmpty()) } }
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
    // E-24, DESIGN-98 A: the sheet of the trips has the three heights of the prototype, the middle first.
    var level by rememberSaveable { mutableStateOf(1) }
    androidx.activity.compose.BackHandler(enabled = globeOn && level == 2) { level = 1 }
    // Low sheet, more globe: it keeps its centre above the sheet.
    val globeShare by androidx.compose.animation.core.animateFloatAsState(if (level == 0) 0.85f else 0.62f, label = "globe-share")
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
            cycleLook = true,
            accent = fr.familia.app.core.theme.LocalClayStyle.current.tabPill,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(globeShare).align(Alignment.TopCenter),
        )
    }
    CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
        Scaffold(
            containerColor = skyColor,
            contentColor = TripPathColors.Ink,
            topBar = {
                Column(Modifier.background(skyColor).statusBarsPadding()) {
                    TripsTopBar(onBack, onDark = dark, onOpenCarnet = onOpenCarnet)
                }
            },
            // E-28: the yellow pill of the prototype, floating bottom right over the list, not a dark bar.
            floatingActionButton = {
                if (success?.canCreate == true) NewTripButton(onAddTrip)
            },
        ) { padding ->
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
              val sheet by androidx.compose.animation.core.animateDpAsState(
                  if (globeOn) maxHeight * backdropSheetShare(level) else maxHeight,
                  label = "trips-sheet",
              )
              if (globeOn && level < 2) {
                GlobeAnimSwitch(
                    on = !globeStopped,
                    onToggle = { globeStopped = !globeStopped },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = sheet + 4.dp),
                )
              }
              Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(sheet)
                    .then(if (globeOn) Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(TripPathColors.Sky) else Modifier),
              ) {
              if (globeOn) SheetHandle(level, onLevel = { level = it.coerceIn(0, 2) })
              // E-29: « À venir | Souvenirs (3) » centred just under the handle. The hand-added destinations (règle 38)
              // count too: a member with only those sees « Souvenirs ».
              if (success != null && !(success.isEmpty && handAdded.isEmpty())) {
                  TripsSwitch(
                      souvenirs, success.past.size + handAdded.size, onChoose = { souvenirs = it },
                      Modifier.align(Alignment.CenterHorizontally).padding(start = 16.dp, end = 16.dp, top = if (globeOn) 0.dp else 8.dp, bottom = 8.dp),
                  )
              }
              if (saving) BatterySavingBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
              Box(Modifier.weight(1f).fillMaxWidth()) {
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
                        state.isEmpty && handAdded.isEmpty() && !state.canCreate -> EmptyState(
                            icon = Icons.Outlined.CardTravel,
                            title = stringResource(R.string.trips_empty_title),
                            body = stringResource(R.string.trips_empty_body_child),
                            action = onOpenAlbums?.let { open -> { ClayLink(stringResource(R.string.trips_empty_albums), onClick = open) } },
                            mascot = true,
                        )
                        souvenirs -> Souvenirs(state, openTrip, handAdded, onOpenCarnet)
                        else -> Coming(state, openTrip, withLandscape = !globeOn, photos = placePhotos)
                    }
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

/** « À venir | Souvenirs (3) » (E-29): a white pill centred, the chosen one a dark pill with light text. */
@Composable
private fun TripsSwitch(souvenirs: Boolean, pastCount: Int, onChoose: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false, true).forEach { past ->
            val chosen = souvenirs == past
            val label = stringResource(if (past) R.string.trips_souvenirs else R.string.trips_upcoming)
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (chosen) TripPathColors.Ink else Color.White)
                    .selectable(selected = chosen, role = Role.Tab, onClick = { onChoose(past) })
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (past) "$label ($pastCount)" else label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (chosen) Color.White else TripPathColors.Ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The small dark « Anim » box at the bottom right of the globe (E-39, prototype): it stops and starts
 * the rotation (an animation longer than 5 s can always be stopped). 56 dp to touch.
 */
@Composable
private fun GlobeAnimSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.globe_list_anim_description)
    val state = stringResource(if (on) R.string.globe_list_anim_on else R.string.globe_list_anim_off)
    Box(
        modifier
            .size(56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description; stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.globe_list_anim),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (on) 1f else 0.6f),
            textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xD917333F))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** « + Nouveau voyage » (E-28, E-37): the accent yellow of the bottom bar's pill, 56 dp high, dark bold text, with its shadow. */
@Composable
private fun NewTripButton(onClick: () -> Unit) {
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        containerColor = fr.familia.app.core.theme.LocalClayStyle.current.tabPill,
        contentColor = TripPathColors.Ink,
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.trips_add), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** « À venir »: the landscape to the next trip and its card on the dune, then the others on the sand. */
@Composable
private fun Coming(
    state: TripsUiState.Success,
    onOpenTrip: (String) -> Unit,
    withLandscape: Boolean = true,
    photos: PlacePhotos? = null,
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        val landscape = hero?.trip?.landscape ?: TripLandscape.Dunes
        // E-36: over the globe there is no landscape, so no sand band under the cards either.
        val ground = if (withLandscape) landscape.palette.near else Color.Transparent
        if (withLandscape) item(key = "landscape") { TripsLandscape(goal, stringResource(R.string.day_today), landscape = landscape) }
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().background(ground).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (hero != null) {
                    HeroCard(hero, plan.heroOngoing, state.today, onOpenTrip, photos)
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

/** « Dans 142 nuits », « Demain », « Aujourd'hui » (récit 109: the nights before the departure). */
@Composable
private fun waitLabel(trip: Trip, today: LocalDate): String = when (val days = daysUntil(trip, today)) {
    0 -> stringResource(R.string.day_today)
    1 -> stringResource(R.string.day_tomorrow)
    else -> pluralStringResource(R.plurals.trips_in_nights, days, days)
}

/** What TalkBack reads of a trip: « La Samoure, du dimanche 11 au vendredi 16 avril 2027, dans 142 jours, avec Inès et Hugo ». */
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

/**
 * The card of the next trip (E-30, E-31, E-32), as the prototype: the photo of the place fills the
 * card; over it « Voyage programmé » top left, « Dans 142 nuits » top right, and at the bottom, on a
 * dark gradient, the name then the month, the days and the kilometres. The whole card opens the trip;
 * Étapes, À prévoir and Comptes are only inside it.
 */
@Composable
private fun HeroCard(
    summary: TripSummary,
    ongoing: Boolean,
    today: LocalDate,
    onOpenTrip: (String) -> Unit,
    photos: PlacePhotos? = null,
) {
    val trip = summary.trip
    val wait = if (ongoing) stringResource(R.string.trips_day_of, tripDayNumber(trip, today), tripLength(trip)) else waitLabel(trip, today)
    val description = tripDescription(summary, wait.lowercase())
    val openLabel = stringResource(R.string.trips_open_action)
    val days = tripLength(trip)
    val figures = listOfNotNull(
        HeroFigure(fr.familia.app.core.ui.frenchDate(trip.startDate, "MMMM").replaceFirstChar { it.titlecase() }, trip.startDate.year.toString()),
        HeroFigure("$days", pluralStringResource(R.plurals.trips_figure_days, days)),
        summary.distanceKm?.let { HeroFigure(java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(it), stringResource(R.string.trips_figure_km)) },
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = { onOpenTrip(trip.id) })
            .clearAndSetSemantics { contentDescription = "$description. $openLabel" },
    ) {
        // The photo of the place (règles 10, 12), the drawn landscape when there is none: the labels always show (E-30).
        PlaceCover(
            place = summary.placeName,
            point = summary.place,
            landscape = trip.landscape,
            photos = photos,
            modifier = Modifier.fillMaxSize(),
            creditAlignment = Alignment.TopStart,
            creditTop = 50.dp,
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD917333F))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
            Text(
                stringResource(if (ongoing) R.string.trips_now_label else R.string.trip_cover_planned),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(
            wait,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TripPathColors.Ink,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(trip.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                figures.forEach { figure ->
                    Column {
                        Text(figure.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        Text(figure.label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A figure under the name of the next trip: « avril » over « 2027 », « 6 » over « JOURS ». */
private data class HeroFigure(val value: String, val label: String)

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

/** A card of « Souvenirs »: a past trip, or a destination added by hand to the carnet (règle 38). */
private sealed interface SouvenirItem {
    val date: LocalDate
    val key: String

    data class Trip(val summary: TripSummary) : SouvenirItem {
        override val date: LocalDate get() = summary.trip.startDate
        override val key: String get() = "past-" + summary.trip.id
    }

    data class HandAdded(val place: TravelPlace) : SouvenirItem {
        override val date: LocalDate get() = place.date
        override val key: String get() = "hand-" + place.id
    }
}

/** « Souvenirs »: the past trips, most recent first, two by row. */
@Composable
private fun Souvenirs(state: TripsUiState.Success, onOpenTrip: (String) -> Unit, handAdded: List<TravelPlace> = emptyList(), onOpenCarnet: (() -> Unit)? = null) {
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    // The trips and the hand-added destinations (règle 38) together, the most recent first.
    val cards = (state.past.map { SouvenirItem.Trip(it) } + handAdded.map { SouvenirItem.HandAdded(it) }).sortedByDescending { it.date }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Text(
                stringResource(if (cards.isEmpty()) R.string.trips_souvenirs_empty else R.string.trips_souvenirs_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = TripPathColors.InkSoft,
            )
        }
        items(cards.chunked(columns), key = { row -> row.first().key }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card ->
                    when (card) {
                        is SouvenirItem.Trip -> SouvenirCard(card.summary, Modifier.weight(1f)) { onOpenTrip(card.summary.trip.id) }
                        is SouvenirItem.HandAdded -> HandAddedCard(card.place, Modifier.weight(1f), onOpenCarnet)
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
            TripSummary(trip("2", "La Samoure", LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 16)), listOf("Camille", "Inès", "Noé"), isParticipant = true),
            TripSummary(trip("4", "Annecy", LocalDate.of(2027, 7, 3), LocalDate.of(2027, 7, 10)), listOf("Inès", "Noé")),
        ),
        past = listOf(TripSummary(trip("3", "Ski à Morzine", LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 20)), listOf("Inès"))),
        today = LocalDate.of(2026, 11, 20),
    )
}

@FamiliaPreviews
@Composable
private fun TripsScreenPreview() {
    FamiliaTheme { Surface { TripsScreen(previewTrips(), {}, {}, {}, {}) } }
}
