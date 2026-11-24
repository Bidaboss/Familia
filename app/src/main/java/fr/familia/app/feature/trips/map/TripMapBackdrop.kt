package fr.familia.app.feature.trips.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.power.rememberBatterySaving
import fr.familia.app.feature.trips.detail.SheetHandle
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.mapLabels
import fr.familia.app.feature.trips.detail.tripTimeline
import fr.familia.app.feature.trips.list.animationsOff
import fr.familia.app.feature.trips.list.talkBackOn
import kotlinx.coroutines.delay

/** The share of the screen the sheet takes at each of its three heights (règle 8): low, high, whole screen. */
internal fun backdropSheetShare(level: Int): Float = when (level) {
    0 -> 0.6f
    1 -> 0.85f
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
    const val OUT_MS = 1_100L
    const val IN_MS = 1_500L
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
    content: @Composable (inSheet: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val placed = remember(steps) { steps.filter { it.hasPosition } }
    var failed by rememberSaveable { mutableStateOf(false) }
    val allowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    // Règle 46: no satellite map while the battery is saved; the screen opens as a plain page.
    val saving = rememberBatterySaving()
    val withMap = satelliteMapUsable(token, allowed && !failed && !saving, talkBack = false) && placed.isNotEmpty()
    val playArrival = playsEditArrival(arrival, withMap, remember { animationsOff(context) })
    // Played once: back from a page opened here, or the screen turned, it does not come again.
    var arrivalDone by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(0) }
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
    LaunchedEffect(keyboard, withMap) { if (keyboard && withMap) level = 2 }
    // Retour first lowers the sheet; the screen's own Retour (ask before leaving) comes after it.
    BackHandler(enabled = withMap && level > 0) { level -= 1 }
    // One layout with or without the map: [content] keeps its place, so it is not made again when the steps arrive.
    BoxWithConstraints(Modifier.fillMaxSize().background(TripPathColors.Sky)) {
        val low = maxHeight * backdropSheetShare(0)
        val sheet by animateDpAsState(if (withMap) maxHeight * backdropSheetShare(level) else maxHeight, label = "backdrop-sheet")
        if (withMap) {
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
                onFailed = { GlobeHealth.markFailed(context); failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheet)
                .clip(RoundedCornerShape(topStart = if (withMap) 28.dp else 0.dp, topEnd = if (withMap) 28.dp else 0.dp))
                .background(TripPathColors.Sky),
        ) {
            if (withMap) SheetHandle(level, onLevel = { level = it.coerceIn(0, 2) })
            // Low or high, the sheet is far from the status bar: the bars of the screens inside do not leave room for it.
            Column(
                Modifier.weight(1f).fillMaxWidth().then(if (withMap && level < 2) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) { content(withMap) }
        }
    }
}
