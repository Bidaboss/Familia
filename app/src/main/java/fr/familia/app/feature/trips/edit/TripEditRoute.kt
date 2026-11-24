package fr.familia.app.feature.trips.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import fr.familia.app.feature.trips.PlaceCover
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.core.place.Place
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/**
 * Connects the trip form to its ViewModel. [onSaved] gets the trip to show (a new one opens at
 * once) and the name of its destination when one was chosen, [onDeleted] closes the trip too,
 * [onBack] leaves without saving. A new trip starts
 * with the question of its destination (récit 95).
 */
@Composable
fun TripEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String?,
    onSaved: (tripId: String, firstPlace: String?) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TripEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TripEditViewModel(
                    container?.tripRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(tripId == null) }
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }
    var search by remember { mutableStateOf<DestinationSearch>(DestinationSearch.Idle) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) {
        val done = state as? TripEditUiState.Done ?: return@LaunchedEffect
        if (done.deleted) onDeleted() else done.tripId?.let { onSaved(it, chosen?.let(::destinationName)) }
    }
    if (asking && state is TripEditUiState.Editing) {
        TripDestinationScreen(
            query = query,
            search = search,
            chosen = chosen,
            onQueryChange = { query = it; chosen = null; search = DestinationSearch.Idle },
            onSearch = {
                val typed = query.trim()
                if (typed.isNotEmpty()) {
                    search = DestinationSearch.Searching
                    scope.launch {
                        val places = container?.placeLocator?.suggest(typed).orEmpty()
                        search = if (places.isEmpty()) DestinationSearch.NothingFound else DestinationSearch.Found(places)
                    }
                }
            },
            onPick = { chosen = it },
            onContinue = {
                chosen?.let { place -> viewModel.update { form -> if (form.title.isBlank()) form.copy(title = destinationName(place)) else form } }
                asking = false
            },
            onTypeAName = { chosen = null; asking = false },
            onBack = onBack,
        )
        return
    }
    // The image of the place (règle 12): the destination chosen for a new trip, the first placed step of a trip.
    val firstStep by remember(tripId) {
        tripId?.let { id -> container?.tripRepository?.observeSteps(id)?.map { steps -> steps.firstOrNull { it.hasPosition } ?: steps.firstOrNull() } }
            ?.catch { e -> if (e is kotlinx.coroutines.CancellationException) throw e else emit(null) }
            ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val coverPlace = chosen?.let(::destinationName) ?: firstStep?.place?.substringBefore(',')?.trim()
    val coverPoint = chosen?.let { GeoPoint(it.lat, it.lng) } ?: firstStep?.takeIf { it.hasPosition }?.let { GeoPoint(it.lat!!, it.lng!!) }
    val landscape = (state as? TripEditUiState.Editing)?.form?.landscape ?: fr.familia.app.core.model.TripLandscape.Dunes
    TripEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack,
        cover = { modifier ->
            PlaceCover(coverPlace, coverPoint, landscape, container?.placePhotos, fr.familia.app.BuildConfig.MAPBOX_PUBLIC_TOKEN, modifier)
        },
    )
}

/** Keeps the chosen destination when Android recreates the screen. */
private val PlaceSaver = listSaver<Place?, Any?>(
    save = { place -> if (place == null) emptyList() else listOf(place.lat, place.lng, place.name, place.address) },
    restore = { saved ->
        if (saved.isEmpty()) null else Place(saved[0] as Double, saved[1] as Double, saved[2] as String?, saved[3] as String?)
    },
)
