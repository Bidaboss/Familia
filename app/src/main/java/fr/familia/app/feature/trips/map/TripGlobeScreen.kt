package fr.familia.app.feature.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
import kotlinx.coroutines.delay

/**
 * « Lancer le voyage » (récit 94): the globe, then the camera flies from step to step. Never starts
 * alone. When the globe cannot start (no token, no network, an error of the SDK) the screen leaves
 * for the flat map by itself, which stays the reference.
 */
@Composable
fun TripGlobeRoute(container: AppContainer?, currentMember: Member, tripId: String, token: String, onBack: () -> Unit) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-globe-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TripGlobeScreen(state, token, onBack)
}

@Composable
fun TripGlobeScreen(state: TripDetailUiState, token: String, onBack: () -> Unit) {
    val success = state as? TripDetailUiState.Success
    Scaffold(
        containerColor = TripPathColors.Sky,
        topBar = {
            FamiliaTopBar(
                title = { Text(success?.trip?.title ?: stringResource(R.string.trip_map_title), maxLines = 1) },
                navigationIcon = { BackButton(onClick = onBack) },
                containerColor = TripPathColors.Sky,
                contentColor = TripPathColors.Ink,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (success == null) {
                LoadingState(Modifier.align(Alignment.Center))
            } else {
                val placed = remember(success.steps) { success.steps.filter { it.hasPosition } }
                if (placed.size < 2 || token.isBlank()) {
                    LaunchedEffect(Unit) { onBack() }
                } else {
                    GlobeContent(placed.map { GeoPoint(it.lat!!, it.lng!!) }, placed.map { it.place to frenchDate(it.departDate, "EEEE d MMMM") }, token, onBack)
                }
            }
        }
    }
}

@Composable
private fun GlobeContent(points: List<GeoPoint>, labels: List<Pair<String, String>>, token: String, onBack: () -> Unit) {
    val engine = remember(points) { FlightEngine(points) }
    var state by remember(engine) { mutableStateOf(engine.state) }
    var commandId by remember(engine) { mutableIntStateOf(0) }
    var ready by remember(engine) { mutableStateOf(false) }
    // One engine call, then what it says is drawn.
    fun act(block: FlightEngine.() -> Unit) {
        val before = engine.state.phase
        engine.block()
        state = engine.state
        // The camera moves only when the phase changes: a pause or a resume leaves it where it is.
        if (engine.state.phase != before) commandId++
    }
    // The pause on a step ends by itself while playing; a pause stops the timer.
    LaunchedEffect(state, commandId) {
        if (state.playing && state.phase is FlightPhase.Dwelling) {
            delay(FlightEngine.DWELL_MILLIS)
            act { onDwellEnded() }
        }
    }
    // The style has 10 s to load: after that the globe is given up for the flat map.
    LaunchedEffect(engine) {
        delay(10_000L)
        if (!ready) onBack()
    }
    val target = engine.camera() ?: return
    val millis = when (state.phase) {
        is FlightPhase.Flying -> engine.flyMillis()
        FlightPhase.Done -> 2_500L
        else -> 0L
    }
    Box(Modifier.fillMaxSize()) {
        GlobeMapView(
            token = token,
            points = points,
            command = GlobeCommand(commandId, target, millis, engine.completedLegs()),
            onArrived = { id -> if (id == commandId && state.phase is FlightPhase.Flying) act { onMotionEnded() } },
            onStyleReady = { ready = true },
            onFailed = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val index = engine.currentIndex
            when (state.phase) {
                FlightPhase.Overview -> {
                    Text(stringResource(R.string.globe_help), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
                    Button(onClick = { act { start() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = TripPathColors.Ink)) {
                        Text(stringResource(R.string.globe_launch))
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.globe_direct)) }
                }
                FlightPhase.Done -> {
                    Text(stringResource(R.string.globe_done), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
                    Button(onClick = { act { reset() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = TripPathColors.Ink)) {
                        Text(stringResource(R.string.globe_replay))
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.globe_see_map)) }
                }
                else -> {
                    labels.getOrNull(index)?.let { (place, date) ->
                        Text(stringResource(R.string.globe_step, index + 1, labels.size), style = MaterialTheme.typography.labelLarge, color = TripPathColors.InkSoft)
                        Text(place, style = MaterialTheme.typography.titleLarge, color = TripPathColors.Ink)
                        Text(date, style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
                    }
                    SideBySide { item ->
                        OutlinedButton(onClick = { act { previous() } }, modifier = item.heightIn(min = 56.dp)) { Text(stringResource(R.string.globe_previous)) }
                        OutlinedButton(onClick = { act { if (state.playing) pause() else resume() } }, modifier = item.heightIn(min = 56.dp)) {
                            Text(stringResource(if (state.playing) R.string.globe_pause else R.string.globe_resume))
                        }
                        OutlinedButton(onClick = { act { next() } }, modifier = item.heightIn(min = 56.dp)) { Text(stringResource(R.string.globe_next)) }
                    }
                    OutlinedButton(onClick = { act { skip() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.globe_see_map)) }
                }
            }
        }
    }
}
