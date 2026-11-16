package fr.familia.app.feature.trips.edit

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

/**
 * Connects the trip form to its ViewModel. [onSaved] gets the trip to show (a new one opens at
 * once), [onDeleted] closes the trip too, [onBack] leaves without saving.
 */
@Composable
fun TripEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String?,
    onSaved: (tripId: String) -> Unit,
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
    LaunchedEffect(state) {
        val done = state as? TripEditUiState.Done ?: return@LaunchedEffect
        if (done.deleted) onDeleted() else done.tripId?.let(onSaved)
    }
    TripEditScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onDelete = viewModel::delete, onBack = onBack)
}
