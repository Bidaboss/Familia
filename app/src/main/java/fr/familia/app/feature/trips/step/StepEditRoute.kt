package fr.familia.app.feature.trips.step

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

/** Connects the step form to its ViewModel, and closes it once saved or deleted. */
@Composable
fun StepEditRoute(container: AppContainer?, currentMember: Member, tripId: String, stepId: String?, onDone: () -> Unit, presetPlace: String? = null) {
    val viewModel: StepEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StepEditViewModel(
                    container?.tripRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    stepId,
                    presetPlace = presetPlace,
                    locator = container?.placeLocator,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == StepEditUiState.Done) onDone()
    }
    StepEditScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onDelete = viewModel::delete, onBack = onDone)
}
