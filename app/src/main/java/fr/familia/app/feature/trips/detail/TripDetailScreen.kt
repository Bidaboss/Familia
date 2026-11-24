package fr.familia.app.feature.trips.detail

import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.share.ShareButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.GlobeHealth
import fr.familia.app.feature.trips.map.TripSatelliteMap
import fr.familia.app.feature.trips.list.animationsOff
import fr.familia.app.feature.trips.list.talkBackOn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import fr.familia.app.feature.trips.TripCountdown
import fr.familia.app.feature.trips.tripCountdown
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.feature.trips.list.TripPart
import fr.familia.app.core.album.AlbumPhotosSection
import fr.familia.app.core.album.AlbumPhotosUiState
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripItem
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
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

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
    checklist: ChecklistActions = ChecklistActions(),
    photos: AlbumPhotosUiState = AlbumPhotosUiState(),
    photoActions: PhotoActions = PhotoActions(),
    /** « Comptes du voyage » (récit 16 bis), shown to the participants only; null while it is off. */
    onOpenExpenses: (() -> Unit)? = null,
    /** « Partager » (récit 31): null while the switch is off. */
    onShare: (() -> Unit)? = null,
    /** The illustrated path of R1 bis (maquette R6). */
    illustrated: Boolean = false,
    /** « 18 photos » of the illustrated path: the album of the trip. */
    onSeePhotos: () -> Unit = {},
    /**
     * « Ma valise » (récit 62), while packingList is on: « À prévoir » becomes « Pour tout le monde »
     * in « À emporter », with these bags under it. Null: the checklist as before.
     */
    packing: (@Composable () -> Unit)? = null,
    /** The part to show first (récit 80: « Étapes » or « À prévoir » from the list); null: the top. */
    openAt: TripPart? = null,
    /** « La carte » (récit 87), offered once the trip has a step; null elsewhere. */
    onOpenMap: (() -> Unit)? = null,
    /** « Le mot de l'étape » (récit 88), from a step. */
    onOpenWord: ((stepId: String) -> Unit)? = null,
    /** « Valise » (récit 98), for those who go; the number of things left to do on the round button. */
    onOpenPacking: (() -> Unit)? = null,
    packingCount: Int = 0,
    /** « Phrases utiles » (récit 105): offered when a step is abroad, in a language of the table; null elsewhere. */
    onOpenPhrases: (() -> Unit)? = null,
    /** « Sécurité » (récit 105): offered when a step is in a country of the table of numbers; null elsewhere. */
    onOpenSafety: (() -> Unit)? = null,
    /** « Monnaie » (récit 105): for those who go, on a trip abroad or with a currency; null elsewhere. */
    onOpenMoney: (() -> Unit)? = null,
    /** « Documents » (récit 105, règle 41): for those who go; null for the followers. */
    onOpenDocuments: (() -> Unit)? = null,
    /** Public Mapbox token of this APK: with it the home is the satellite map and a sheet (récit 100); blank: the drawn path. */
    satelliteToken: String = "",
    /** The home of a planned trip (règle 10): the image of the place, or several going by. */
    cover: (@Composable (TripDetailUiState.Success) -> Unit)? = null,
    /** « Le trajet » (règle 16): open a step, − / + on the nights; null: open only. */
    timeline: TimelineActions? = null,
    /** « Enrichir avec une IA » (récit 106, règle 30): a small button under the trip, for those who go; null elsewhere. */
    onOpenEnrich: (() -> Unit)? = null,
) {
    val success = state as? TripDetailUiState.Success
    val path = illustrated && success != null
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = {
                    if (path && success != null) {
                        PathTitle(success)
                    } else {
                        Text(success?.trip?.title ?: stringResource(R.string.trip_title), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                // The sky of the trip's landscape goes on under the bar (récit 81).
                containerColor = if (path) success?.trip?.landscape?.palette?.sky ?: TripPathColors.Sky else Color.Transparent,
                contentColor = if (path) TripPathColors.Ink else Color.Unspecified,
                actions = {
                    if (success?.canManage == true) {
                        TextButton(
                            onClick = onEditTrip,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(R.string.trip_edit))
                        }
                    }
                    if (onShare != null && success != null) ShareButton(onClick = onShare)
                    HelpButton(ScreenHelp.TripDetail)
                },
            )
        },
        floatingActionButton = {
            if (success?.isParticipant == true) {
                ExtendedFloatingActionButton(
                    onClick = onAddStep,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.trip_add_step)) },
                    text = { Text(stringResource(R.string.trip_add_step)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TripDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
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
                is TripDetailUiState.Success -> if (illustrated) {
                    PathContent(state, onEditStep, onArrived, onClearArrival, checklist, photos, photoActions, onOpenExpenses, onSeePhotos, packing, openAt, onOpenMap, onOpenWord, onOpenPacking, packingCount, satelliteToken, onOpenPhrases, onOpenSafety, onOpenMoney, cover, timeline, onOpenDocuments, onOpenEnrich)
                } else {
                    Content(state, onEditStep, onArrived, onClearArrival, checklist, photos, photoActions, onOpenExpenses, packing, onOpenMap, onOpenWord)
                }
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
    checklist: ChecklistActions,
    photos: AlbumPhotosUiState,
    photoActions: PhotoActions,
    onOpenExpenses: (() -> Unit)?,
    packing: (@Composable () -> Unit)?,
    onOpenMap: (() -> Unit)? = null,
    onOpenWord: ((stepId: String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the add button never hides the last step.
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "summary") { Summary(state) }
        if (state.sendFailed) {
            item(key = "failed") {
                Text(
                    stringResource(R.string.trip_arrival_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item(key = "steps-title") { TripSectionTitle(stringResource(R.string.trip_steps)) }
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
                onOpenWord = onOpenWord?.let { open -> { open(step.id) } },
            )
        }
        // The steps come first: on the way, « Bien arrivé » matters more than the figures, the photos and what to bring.
        tripFigures(state.trip, state.steps, photos.photos.size)?.let { figures ->
            item(key = "figures") { TripFiguresTile(figures) }
        }
        item(key = "photos-title") { TripSectionTitle(stringResource(R.string.trip_photos)) }
        item(key = "photos") {
            val empty = if (photos.canAdd) R.string.trip_photos_empty_participant else R.string.trip_photos_empty
            AlbumPhotosSection(photos, photoActions, stringResource(empty))
        }
        // « À prévoir » is for those who go (récit 86): a follower sees the steps, the photos and « bien arrivé ».
        if (state.isParticipant) {
            item(key = "checklist-title") { TripSectionTitle(stringResource(if (packing != null) R.string.packing_title else R.string.trip_checklist)) }
            item(key = "checklist") { PackingOrChecklist(state, checklist, packing) }
        }
        if (onOpenMap != null && state.steps.isNotEmpty()) {
            item(key = "map") { ClayLink(stringResource(R.string.trip_map_title), onClick = onOpenMap) }
        }
        if (onOpenExpenses != null && state.isParticipant) {
            item(key = "expenses") {
                ClayLink(stringResource(R.string.expenses_open), onClick = onOpenExpenses)
            }
        }
    }
}

/** « Séville », and under it who goes: « Avec Camille, Inès et Noé ». */
@Composable
private fun PathTitle(state: TripDetailUiState.Success) {
    Column {
        Text(state.trip.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            joinNames(state.participants),
            style = MaterialTheme.typography.labelLarge,
            color = TripPathColors.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The trip as an illustrated path (R1 bis, maquette R6): the strip of the days on the sky, the path
 * through the days around the chosen one, then the chosen day with « Photos » and
 * « Comptes ». The carnet below holds the steps of that day, what to bring and the photos.
 */
@Composable
private fun PathContent(
    state: TripDetailUiState.Success,
    onEditStep: (String) -> Unit,
    onArrived: (String) -> Unit,
    onClearArrival: (String) -> Unit,
    checklist: ChecklistActions,
    photos: AlbumPhotosUiState,
    photoActions: PhotoActions,
    onOpenExpenses: (() -> Unit)?,
    onSeePhotos: () -> Unit,
    packing: (@Composable () -> Unit)?,
    openAt: TripPart? = null,
    onOpenMap: (() -> Unit)? = null,
    onOpenWord: ((stepId: String) -> Unit)? = null,
    onOpenPacking: (() -> Unit)? = null,
    packingCount: Int = 0,
    satelliteToken: String = "",
    onOpenPhrases: (() -> Unit)? = null,
    onOpenSafety: (() -> Unit)? = null,
    onOpenMoney: (() -> Unit)? = null,
    cover: (@Composable (TripDetailUiState.Success) -> Unit)? = null,
    timeline: TimelineActions? = null,
    onOpenDocuments: (() -> Unit)? = null,
    onOpenEnrich: (() -> Unit)? = null,
) {
    val days = remember(state.trip.startDate, state.trip.endDate) { TripDays.of(state.trip) }
    // The day of the card: today during the trip, else its first day (no strip of days any more: « Le trajet » shows them all).
    val chosen = TripDays.firstShown(days, state.today)
    val number = days.indexOf(chosen) + 1
    val daySteps = TripDays.stepsOf(state.steps, chosen, days)
    val places = daySteps.map { it.place }.distinct()
    val label = when {
        chosen == state.today && places.isNotEmpty() -> stringResource(R.string.trip_day_today_place, places.first())
        chosen == state.today -> stringResource(R.string.day_today)
        else -> places.firstOrNull()
    }
    val listState = rememberLazyListState()
    val withCover = cover != null && state.today.isBefore(state.trip.startDate)
    // « Bien arrivé » stays at hand: the next step not arrived yet, for those who go.
    val nextStep = remember(state.steps) { state.steps.filter { it.arrival == null }.minWithOrNull(compareBy({ it.departDate }, { it.departTime })) }
    val showNext = nextStep != null && state.isParticipant
    val notebookIndex = (if (withCover) 1 else 0) + 1 + (if (state.sendFailed) 1 else 0) + (if (showNext) 2 else 0)
    // Opened from « Étapes » or « À prévoir » of the list: that part first, once.
    var opened by rememberSaveable(state.trip.id) { mutableStateOf(false) }
    LaunchedEffect(openAt) {
        if (opened || openAt == null) return@LaunchedEffect
        opened = true
        when (openAt) {
            TripPart.Steps -> listState.scrollToItem(notebookIndex)
            // « À prévoir » has its own screen now (récit 98): the list opens « Valise » directly.
            TripPart.ToBring -> Unit
            TripPart.Expenses -> Unit
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val placed = remember(state.steps) { state.steps.filter { it.hasPosition } }
    var mapFailed by rememberSaveable { mutableStateOf(false) }
    val mapAllowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    val satellite = satelliteToken.isNotBlank() && mapAllowed && !mapFailed && placed.isNotEmpty()
    // The sheet has three heights: low (the map above), high, and the whole screen (règle 8).
    var level by rememberSaveable(state.trip.id) { mutableStateOf(0) }
    val expanded = level >= 1
    var selected by rememberSaveable(state.trip.id) { mutableStateOf<Int?>(null) }
    // Back first puts the map back as it was (règle 25, « Retour dézoome »), then lowers the sheet.
    androidx.activity.compose.BackHandler(enabled = satellite && (selected != null || level > 0)) {
        if (selected != null) selected = null else level -= 1
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val lowHeight = maxHeight * 0.4f
    val sheetHeight by androidx.compose.animation.core.animateDpAsState(
        when (level) { 0 -> lowHeight; 1 -> maxHeight * 0.85f; else -> maxHeight },
        label = "sheet",
    )
    if (satellite) {
        val next = remember(state.steps) { placed.indexOfFirst { it.arrival == null } }
        TripSatelliteMap(
            token = satelliteToken,
            points = placed.map { GeoPoint(it.lat!!, it.lng!!) },
            names = placed.map { it.place },
            arrived = placed.map { it.arrival != null },
            labels = remember(state.steps) { mapLabels(placed, tripTimeline(state.trip, state.steps)) },
            nextIndex = next,
            selected = selected,
            onSelect = { selected = it },
            intro = !animationsOff(context),
            sheetHigh = expanded,
            bottomPaddingDp = lowHeight.value.toDouble(),
            onFailed = { GlobeHealth.markFailed(context); mapFailed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .then(if (satellite) Modifier.height(sheetHeight).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(TripPathColors.Sky) else Modifier.fillMaxSize()),
    ) {
    if (satellite) SheetHandle(level, onLevel = { level = it.coerceIn(0, 2) })
    LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        // Room at the bottom so the add button never hides the last line.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (withCover && cover != null) {
            item(key = "cover") { Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) { cover(state) } }
        }
        if (!satellite) item(key = "path") { TripPathIllustration(days, chosen, state.today, label, landscape = state.trip.landscape) }
        item(key = "day") {
            // The card sits on the bottom of the landscape, as on the maquette.
            Box(Modifier.fillMaxWidth().background(state.trip.landscape.palette.near).padding(horizontal = ClaySpacing.screen, vertical = 12.dp)) {
                TripDayCard(
                    number = number,
                    places = places,
                    day = chosen,
                    photoCount = photos.photos.size,
                    onPhotos = onSeePhotos,
                    onExpenses = onOpenExpenses?.takeIf { state.isParticipant },
                    // The satellite map is already open above the sheet (règle 23): « La carte » stays only for the fallback (TalkBack, no map).
                    onMap = onOpenMap?.takeIf { state.steps.isNotEmpty() && !satellite },
                    onPacking = onOpenPacking?.takeIf { state.isParticipant },
                    packingCount = packingCount,
                    onPhrases = onOpenPhrases,
                    onSafety = onOpenSafety,
                    onMoney = onOpenMoney,
                    onDocuments = onOpenDocuments?.takeIf { state.isParticipant },
                )
            }
        }
        if (state.sendFailed) {
            item(key = "failed") {
                Text(
                    stringResource(R.string.trip_arrival_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
                )
            }
        }
        if (showNext && nextStep != null) {
            item(key = "next-title") {
                TripSectionTitle(stringResource(R.string.timeline_next), Modifier.padding(horizontal = ClaySpacing.screen))
            }
            item(key = "next-${nextStep.id}") {
                StepCard(
                    step = nextStep,
                    today = state.today,
                    isParticipant = state.isParticipant,
                    onEdit = { onEditStep(nextStep.id) },
                    onArrived = { onArrived(nextStep.id) },
                    onClearArrival = { onClearArrival(nextStep.id) },
                    onOpenWord = onOpenWord?.let { open -> { open(nextStep.id) } },
                    modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = ClaySpacing.between / 2),
                )
            }
        }
        item(key = "timeline") {
            Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
                TripTimelineSection(state.trip, state.steps, timeline ?: TimelineActions(onOpen = onEditStep))
            }
        }
        if (onOpenEnrich != null && state.isParticipant) {
            item(key = "enrich") {
                // A small, readable button, only on the trip (règle 30).
                TextButton(
                    onClick = onOpenEnrich,
                    modifier = Modifier.padding(horizontal = ClaySpacing.screen).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Text(stringResource(R.string.enrich_round_label), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        tripFigures(state.trip, state.steps, photos.photos.size)?.let { figures ->
            item(key = "figures") { TripFiguresTile(figures, Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) }
        }
        item(key = "photos-title") {
            TripSectionTitle(stringResource(R.string.trip_photos), Modifier.padding(horizontal = ClaySpacing.screen))
        }
        item(key = "photos") {
            val empty = if (photos.canAdd) R.string.trip_photos_empty_participant else R.string.trip_photos_empty
            Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
                AlbumPhotosSection(photos, photoActions, stringResource(empty))
            }
        }
    }
    }
    }
}

/**
 * The handle of the sheet: a drag up or down goes one height further or back, a touch switches
 * between low and high; on the whole screen an arrow at the top left lowers it (règles 7 and 8).
 */
@Composable
internal fun SheetHandle(level: Int, onLevel: (Int) -> Unit) {
    var dragged by remember { mutableStateOf(0f) }
    val label = stringResource(if (level > 0) R.string.trip_sheet_lower else R.string.trip_sheet_raise)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragged += it },
                onDragStopped = {
                    if (dragged < -40f) onLevel(level + 1) else if (dragged > 40f) onLevel(level - 1)
                    dragged = 0f
                },
            )
            .clickable(role = Role.Button, onClickLabel = label) { onLevel(if (level > 0) 0 else 1) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 48.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(TripPathColors.InkSoft))
        if (level == 2) {
            androidx.compose.material3.IconButton(onClick = { onLevel(1) }, modifier = Modifier.align(Alignment.CenterStart).size(56.dp)) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.trip_sheet_lower),
                    tint = TripPathColors.Ink,
                )
            }
        }
    }
}

/** « À prévoir » as before, or, with packingList (récit 62), « Pour tout le monde » then the bags. */
@Composable
private fun PackingOrChecklist(state: TripDetailUiState.Success, checklist: ChecklistActions, packing: (@Composable () -> Unit)?) {
    if (packing == null) {
        TripChecklist(state.items, state.assignees, state.isParticipant, checklist)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.packing_everyone), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        TripChecklist(state.items, state.assignees, state.isParticipant, checklist)
        packing()
    }
}

/** « 4 nuits avant le départ », « Demain », « Jour 2 sur 6 »; nothing once the trip is over. */
@Composable
private fun CountdownLine(countdown: TripCountdown) {
    val text = when (countdown) {
        is TripCountdown.NightsBefore -> pluralStringResource(R.plurals.trip_countdown_nights, countdown.nights, countdown.nights)
        TripCountdown.Tomorrow -> stringResource(R.string.trip_countdown_tomorrow)
        TripCountdown.DepartureDay -> stringResource(R.string.trip_countdown_today)
        is TripCountdown.During -> stringResource(R.string.trip_countdown_during, countdown.day, countdown.of)
        TripCountdown.Over -> return
    }
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Summary(state: TripDetailUiState.Success) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Family)
    // The point of the screen: when and with whom (DESIGN-11).
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Family, emphasis = true) {
        CountdownLine(tripCountdown(state.trip.startDate, state.trip.endDate, state.today))
        ClayCardTitle(tripDatesLabel(state.trip, state.today), style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier = Modifier,
    onOpenWord: (() -> Unit)? = null,
) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = modifier.fillMaxWidth(),
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
        // The word of the step (récit 88): written by a participant, read by the followers.
        if (onOpenWord != null) {
            TextButton(onClick = onOpenWord, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.word_title)) }
        }
    }
}

/** « Bien arrivé, signalé par Lou à 9 h 52 », with « Annuler » for a participant. */
@Composable
private fun ArrivedRow(arrival: TripArrival, today: LocalDate, isParticipant: Boolean, onClearArrival: () -> Unit) {
    val at = arrival.at?.atZone(FAMILY_ZONE)
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
internal fun previewTripDetail(isParticipant: Boolean = true, withPositions: Boolean = false): TripDetailUiState.Success {
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
        ).let { steps ->
            if (!withPositions) steps else steps.zip(listOf(41.92 to 8.74, 42.27 to 8.70, 41.39 to 9.16)) { s, p -> s.copy(lat = p.first, lng = p.second) }
        },
        canManage = true,
        isParticipant = isParticipant,
        today = start,
        items = listOf(
            TripItem("i1", "Réserver la voiture", "b", "Inès", done = false),
            TripItem("i2", "Crème solaire", null, null, done = false),
            TripItem("i3", "Billets d'avion", "a", "Camille", done = true),
        ),
        assignees = listOf(ItemAssignee("a", "Camille"), ItemAssignee("b", "Inès"), ItemAssignee("c", "Noé")),
    )
}

@FamiliaPreviews
@Composable
private fun TripDetailScreenPreview() {
    FamiliaTheme { Surface { TripDetailScreen(previewTripDetail(), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos()) } }
}

@FamiliaPreviews
@Composable
private fun TripDetailPathPreview() {
    FamiliaTheme {
        Surface {
            TripDetailScreen(
                previewTripDetail().copy(today = LocalDate.of(2027, 8, 3)),
                {}, {}, {}, {}, {}, {}, {},
                photos = previewAlbumPhotos(),
                onOpenExpenses = {},
                illustrated = true,
            )
        }
    }
}
