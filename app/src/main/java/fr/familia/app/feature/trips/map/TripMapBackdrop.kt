package fr.familia.app.feature.trips.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.detail.SheetHandle
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.mapLabels
import fr.familia.app.feature.trips.detail.tripTimeline
import fr.familia.app.feature.trips.list.talkBackOn

/** The share of the screen the sheet takes at each of its three heights (règle 8): low, high, whole screen. */
internal fun backdropSheetShare(level: Int): Float = when (level) {
    0 -> 0.6f
    1 -> 0.85f
    else -> 1f
}

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
    content: @Composable (inSheet: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val placed = remember(steps) { steps.filter { it.hasPosition } }
    var failed by rememberSaveable { mutableStateOf(false) }
    val allowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    val withMap = satelliteMapUsable(token, allowed && !failed, talkBack = false) && placed.isNotEmpty()
    var level by rememberSaveable { mutableStateOf(0) }
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
                intro = false,
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
            Column(Modifier.weight(1f).fillMaxWidth()) { content(withMap) }
        }
    }
}
