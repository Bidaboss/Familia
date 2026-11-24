package fr.familia.app.feature.trips.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.power.rememberBatterySavingAtOpening
import fr.familia.app.feature.trips.detail.SheetHandle
import fr.familia.app.feature.trips.detail.sheetDrag
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.mapLabels
import fr.familia.app.feature.trips.detail.tripTimeline
import kotlinx.coroutines.delay
import fr.familia.app.feature.trips.list.animationsOff
import fr.familia.app.feature.trips.list.talkBackOn

/** The share of the screen the sheet takes at each of its three heights (règle 8, DESIGN-98 A, as on the prototype): 22 % at the bottom, 48 % in the middle, the whole screen. The home of a trip uses the same. */
internal fun backdropSheetShare(level: Int): Float = when (level) {
    0 -> 0.22f
    1 -> 0.48f
    else -> 1f
}

/**
 * The arrival of « Modifier » (récit 111, règle 4), from the reference video: the sheet opens high
 * then goes down, then the camera goes out to the whole globe and dives back on the steps, in one
 * move of about 2.9 s of camera (300 + 1 100 + 1 500 ms, close to the 2.5 s of the prototype), stopped by a touch.
 */
internal object EditArrival {
    /** The sheet stays high this long before it goes down. */
    const val SHEET_HIGH_MS = 600L
    /** After the map is drawn, the camera waits for the sheet to be down. */
    const val CAMERA_WAIT_MS = 300L
    /** DESIGN-101 B: about 6 s in all, slow enough to see the trip arrive, cut by a touch. */
    const val OUT_MS = 2_400L
    const val IN_MS = 3_300L
    /** The arrival of the page of a trip (règle 3): out to the continent, then in on the steps, about 6 s. */
    const val HOME_OUT_MS = 2_600L
    const val HOME_IN_MS = 3_400L
    /**
     * E-61: the arrival in a trip: the whole globe turns this many degrees towards it (fast, then
     * slower), then the camera dives on the steps; about 6 s in all (DESIGN-101 B), stopped by a touch.
     * Under 180: the camera always turns the short way, so the turn goes the same way every time.
     */
    const val ARRIVAL_SPIN_MS = 2_500L
    const val ARRIVAL_SPIN_DEGREES = 170.0
    const val ARRIVAL_ZOOM_MS = 3_200L
    /** The whole Earth in the globe projection of Mapbox Standard. */
    const val GLOBE_ZOOM = 1.0
}

/** The arrival plays for a trip changed with its map, unless the phone asks for no animation. */
internal fun playsEditArrival(asked: Boolean, withMap: Boolean, animationsOff: Boolean): Boolean = asked && withMap && !animationsOff

/** The step of [focusId] among the steps that have a point on the map; null when it has none (a new step). */
internal fun focusIndex(placed: List<TripStep>, focusId: String?): Int? = placed.indexOfFirst { it.id == focusId }.takeIf { it >= 0 }

/**
 * A modification screen of a trip with its map open above it (règles 4, 6 and 9): the satellite map
 * of the steps, free to move and zoom, the step [focusId] chosen; [content] in a sheet that pulls up
 * (60 %, 85 %, the whole screen) and goes down again with Retour, a drag or the arrow (règle 8).
 * TalkBack, no token, a map in failure or no step with a point: [content] alone, as before. The
 * keyboard open raises the sheet to the whole screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripMapBackdrop(
    token: String,
    trip: Trip?,
    steps: List<TripStep>,
    focusId: String?,
    /** « Modifier » of a trip (règle 4): the sheet high then low, the map out to the globe and back. */
    arrival: Boolean = false,
    /** The height the sheet opens at: 0 low, 1 the middle (« Nouvel élément », fiche F2 §4), 2 the whole screen. */
    startLevel: Int = 0,
    content: @Composable (inSheet: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val placed = remember(steps) { steps.filter { it.hasPosition } }
    // A new try each time the screen opens (retour d'essai du 24/11), and « Réessayer » when it failed.
    var failed by remember { mutableStateOf(false) }
    var cause by remember { mutableStateOf<String?>(null) }
    var mapTry by remember { mutableStateOf(0) }
    val allowed = remember { !talkBackOn(context) }
    // Règle 46: no satellite map while the battery is saved; the screen opens as a plain page.
    val saving = rememberBatterySavingAtOpening()
    // The sheet keeps its heights when the map fails (spec A3): the notice takes the place of the map.
    val mapLayout = satelliteMapUsable(token, allowed && !saving, talkBack = false) && placed.isNotEmpty()
    val withMap = mapLayout && !failed
    val playArrival = playsEditArrival(arrival, withMap, remember { animationsOff(context) })
    // Played once: back from a page opened here, or the screen turned, it does not come again.
    var arrivalDone by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(startLevel.coerceIn(0, 2)) }
    // Fiche F2 §4: the screen in the sheet may put its title over the map (round back, title pill, help).
    val header = remember { BackdropHeader() }
    // The steps arrive after the form: the sheet goes from the whole screen to high, then down.
    LaunchedEffect(playArrival) {
        if (!playArrival || arrivalDone) return@LaunchedEffect
        arrivalDone = true
        level = 1
        delay(EditArrival.SHEET_HIGH_MS)
        // The person already moved the sheet: it stays where they put it.
        if (level == 1) level = 0
    }
    var selected by rememberSaveable(focusId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(placed, focusId) { if (selected == null) selected = focusIndex(placed, focusId) }
    // The keyboard open: the sheet takes the whole screen, so the field being typed stays above it.
    val keyboard = WindowInsets.isImeVisible
    LaunchedEffect(keyboard, mapLayout) { if (keyboard && mapLayout) level = 2 }
    // Retour first lowers the sheet; the screen's own Retour (ask before leaving) comes after it.
    BackHandler(enabled = mapLayout && level > 0 && !header.full) { level -= 1 }
    // One layout with or without the map: [content] keeps its place, so it is not made again when the steps arrive.
    BoxWithConstraints(Modifier.fillMaxSize().background(TripPathColors.Sky)) {
        val low = maxHeight * backdropSheetShare(0)
        // Retour E-41: a screen of its own (the search of a place) takes the whole screen, without the handle.
        val sheeted = mapLayout && !header.full
        val sheet by animateDpAsState(if (sheeted) maxHeight * backdropSheetShare(level) else maxHeight, label = "backdrop-sheet")
        if (mapLayout && failed) {
            MapUnavailableNotice(onRetry = { failed = false; mapTry++ }, reason = cause, modifier = Modifier.fillMaxWidth().height(maxHeight - low))
        }
        if (withMap) key(mapTry) {
            TripSatelliteMap(
                token = token,
                points = placed.map { GeoPoint(it.lat!!, it.lng!!) },
                names = placed.map { it.place },
                arrived = placed.map { it.arrival != null },
                labels = remember(trip, steps) { trip?.let { mapLabels(placed, tripTimeline(it, steps)) } },
                nextIndex = placed.indexOfFirst { it.arrival == null },
                selected = selected,
                onSelect = { selected = it },
                intro = playArrival,
                globeIntro = playArrival,
                introDelayMs = EditArrival.CAMERA_WAIT_MS,
                sheetHigh = false,
                bottomPaddingDp = low.value.toDouble(),
                onFailed = { cause = it.detail; failed = true },
                modifier = Modifier.fillMaxSize(),
                settingsAtTop = header.content != null,
            )
        }
        val floating = withMap && level < 2 && !header.full
        if (floating) header.content?.let { title -> Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()) { title() } }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheet)
                .clip(RoundedCornerShape(topStart = if (sheeted) 28.dp else 0.dp, topEnd = if (sheeted) 28.dp else 0.dp))
                .background(TripPathColors.Sky)
                .then(if (sheeted) Modifier.sheetDrag(level) { level = it.coerceIn(0, 2) } else Modifier),
        ) {
            // E-64 (c): at the whole screen, a screen with its own title shows only its bar (one header);
            // Retour lowers the sheet first, as before.
            val ownBar = level == 2 && header.content != null
            if (sheeted && !ownBar) SheetHandle(level, onLevel = { level = it.coerceIn(0, 2) })
            // Low or high, the sheet is far from the status bar: the bars of the screens inside do not leave room for it.
            Column(
                Modifier.weight(1f).fillMaxWidth().then(if (sheeted && level < 2) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                CompositionLocalProvider(LocalBackdropHeader provides header, LocalBackdropHeaderFloating provides floating) { content(mapLayout) }
            }
        }
    }
}

/** The title a screen in the sheet puts over the map (fiche F2 §4); null: its own bar in the sheet. */
class BackdropHeader {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** True while the screen in the sheet asks for the whole screen, without the handle (retour E-41). */
    var full by mutableStateOf(false)
}

/** The header of the [TripMapBackdrop] around, for the screen in its sheet; null outside one. */
val LocalBackdropHeader = androidx.compose.runtime.staticCompositionLocalOf<BackdropHeader?> { null }

/** True while that header shows over the map (map drawn, sheet not at the whole screen). */
val LocalBackdropHeaderFloating = androidx.compose.runtime.compositionLocalOf { false }
