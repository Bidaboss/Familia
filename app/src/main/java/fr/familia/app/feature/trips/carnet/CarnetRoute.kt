package fr.familia.app.feature.trips.carnet

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Member
import kotlinx.serialization.Serializable

/** « Carnet de baroudeur » of the member (récit 107), from the list of the trips. */
@Serializable
data object TripCarnetDestination

/** Connects « Carnet de baroudeur » to its ViewModel and the sheet « Ajouter une destination ». */
@Composable
fun CarnetRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val viewModel: CarnetViewModel = viewModel(
        key = "carnet-${currentMember.id}",
        factory = viewModelFactory {
            initializer {
                CarnetViewModel(
                    trips = container?.tripRepository,
                    photos = container?.albumPhotoRepository,
                    places = container?.travelPlaceRepository,
                    members = container?.memberRepository,
                    locator = container?.placeLocator,
                    memberId = currentMember.id,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var lastName by rememberSaveable { mutableStateOf("") }
    /** « Modifier le voyage »: the id of the first place of the trip open in the sheet, else null. */
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var edited by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val added = stringResource(if (edited) R.string.carnet_edited else R.string.carnet_added, lastName)
    LaunchedEffect(adding) {
        if (adding == AddState.Saved) {
            sheet = false
            editingId = null
            viewModel.resetAdding()
            snackbar.showSnackbar(added)
        }
    }
    val editing = editingId?.let { id -> (state as? CarnetUiState.Ready)?.manual?.let(::manualGroups)?.firstOrNull { it.places.first().id == id } }
    val locator = container?.placeLocator
    CarnetScreen(
        state = state,
        onBack = onBack,
        onAdd = if (container?.travelPlaceRepository != null) ({ viewModel.resetAdding(); editingId = null; sheet = true }) else null,
        onRemove = viewModel::removeTrip,
        snackbar = snackbar,
        onEdit = { trip -> viewModel.resetAdding(); editingId = trip.places.first().id; sheet = true },
    )
    if (sheet && (editingId == null || editing != null)) {
        // A new key for each trip: the sheet starts again from what is saved.
        androidx.compose.runtime.key(editingId) { AddTravelPlaceSheet(
            today = (state as? CarnetUiState.Ready)?.today ?: familyToday(),
            saving = adding == AddState.Saving,
            failed = adding == AddState.Failed,
            suggest = { query -> locator?.suggest(query, 6).orEmpty() },
            onSave = { name, date, stops ->
                lastName = name ?: fr.familia.app.feature.trips.edit.destinationName(stops.first().first)
                edited = editing != null
                viewModel.addTrip(name, date, stops, replacing = editing)
            },
            onDismiss = { sheet = false; editingId = null },
            editing = editing,
            onRemoveTrip = editing?.let { trip -> { sheet = false; editingId = null; viewModel.removeTrip(trip) } },
        ) }
    }
}
