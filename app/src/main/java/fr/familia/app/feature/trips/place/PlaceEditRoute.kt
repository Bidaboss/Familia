package fr.familia.app.feature.trips.place

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripPlaceKind

/**
 * Connects the page of a place to its ViewModel, and closes it once saved or deleted: the one of
 * [placeId], or a new [kind] under the destination [stepId] when [placeId] is null. A new place
 * without [presetName] starts with the search of its kind (règle 20); « Choisir un autre … » opens
 * it again on the page. Back from the search returns to the page, or leaves when nothing was chosen yet.
 */
@Composable
fun PlaceEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    placeId: String?,
    onDone: () -> Unit,
    stepId: String? = null,
    kind: TripPlaceKind? = null,
    presetName: String? = null,
) {
    val viewModel: PlaceEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlaceEditViewModel(
                    trips = container?.tripRepository,
                    places = container?.tripPlaceRepository,
                    members = container?.memberRepository,
                    currentMemberId = currentMember.id,
                    tripId = tripId,
                    placeId = placeId,
                    stepId = stepId,
                    kind = kind,
                    presetName = presetName,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == PlaceEditUiState.Done) onDone()
    }
    // Saved: Android stopping the app in the background brings back the same screen.
    var searching by rememberSaveable { mutableStateOf(placeId == null && presetName == null) }
    var chosen by rememberSaveable { mutableStateOf(placeId != null || presetName != null) }
    val editing = state as? PlaceEditUiState.Editing
    if (searching && editing != null && editing.canEdit) {
        val search: PlaceSearchViewModel = viewModel(
            key = "place_search",
            factory = viewModelFactory {
                initializer {
                    PlaceSearchViewModel(
                        search = container?.placeSearch,
                        kind = editing.kind,
                        lat = editing.destinationLat,
                        lng = editing.destinationLng,
                        savedState = createSavedStateHandle(),
                    )
                }
            },
        )
        val searchState by search.uiState.collectAsStateWithLifecycle()
        PlaceSearchScreen(
            state = searchState,
            destination = editing.destination,
            onType = search::type,
            onRetry = search::retry,
            onChoose = { found -> viewModel.choose(found); chosen = true; searching = false },
            onChooseTyped = { name -> viewModel.chooseTyped(name); chosen = true; searching = false },
            onBack = { if (chosen) searching = false else onDone() },
        )
    } else {
        // Fiche F2 §4: a new place is added under the map of the trip, as a transport.
        if (placeId == null) {
            val trip by androidx.compose.runtime.remember(tripId) {
                container?.tripRepository?.observeTrip(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf(null)
            }.collectAsStateWithLifecycle(initialValue = null)
            val steps by androidx.compose.runtime.remember(tripId) {
                container?.tripRepository?.observeSteps(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList())
            }.collectAsStateWithLifecycle(initialValue = emptyList())
            fr.familia.app.feature.trips.map.TripMapBackdrop(
                token = fr.familia.app.BuildConfig.MAPBOX_PUBLIC_TOKEN,
                trip = trip,
                steps = steps,
                focusId = stepId,
                startLevel = 1,
            ) { inSheet ->
                PlaceEditScreen(
                    state = state,
                    onChange = viewModel::update,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onBack = onDone,
                    onSearch = { searching = true },
                    inSheet = inSheet,
                )
            }
        } else {
            PlaceEditScreen(
                state = state,
                onChange = viewModel::update,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onBack = onDone,
                onSearch = { searching = true },
            )
        }
    }
}
