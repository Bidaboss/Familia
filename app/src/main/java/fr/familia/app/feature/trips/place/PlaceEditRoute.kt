package fr.familia.app.feature.trips.place

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripPlaceKind

/**
 * Connects the page of a place to its ViewModel, and closes it once saved or deleted: the one of
 * [placeId], or a new [kind] under the destination [stepId] when [placeId] is null.
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
    PlaceEditScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onDelete = viewModel::delete, onBack = onDone)
}
