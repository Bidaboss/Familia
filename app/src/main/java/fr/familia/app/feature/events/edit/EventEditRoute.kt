package fr.familia.app.feature.events.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the event form to its ViewModel, and closes it once saved or deleted. */
@Composable
fun EventEditRoute(container: AppContainer?, currentMember: Member, eventId: String?, onDone: () -> Unit) {
    val viewModel: EventEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventEditViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    currentMember.id,
                    eventId,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == EventEditUiState.Done) onDone()
    }
    EventEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
    )
}
