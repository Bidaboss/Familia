package fr.familia.app.feature.polls.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the « Nouveau sondage » form to its ViewModel, and closes it once sent; [eventId]: asked from that event (récit 2 quater). */
@Composable
fun PollEditRoute(container: AppContainer?, currentMember: Member, onDone: () -> Unit, eventId: String? = null) {
    val viewModel: PollEditViewModel = viewModel(
        key = "pollEdit-$eventId",
        factory = viewModelFactory {
            initializer {
                PollEditViewModel(
                    container?.pollRepository, container?.memberRepository, currentMember.id,
                    eventId = eventId, events = container?.eventRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == PollEditUiState.Done) onDone()
    }
    PollEditScreen(
        state = state,
        onChange = viewModel::update,
        onToggle = viewModel::toggle,
        onShortcut = viewModel::applyShortcut,
        onAddOption = viewModel::addOption,
        onRemoveOption = viewModel::removeOption,
        onSave = viewModel::save,
        onBack = onDone,
    )
}
