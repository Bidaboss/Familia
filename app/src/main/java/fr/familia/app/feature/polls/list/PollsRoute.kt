package fr.familia.app.feature.polls.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects « Sondages » to its ViewModel. */
@Composable
fun PollsRoute(
    container: AppContainer?,
    currentMember: Member,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PollsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PollsViewModel(container?.pollRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PollsScreen(state = state, onNew = onNew, onOpen = onOpen, onRetry = viewModel::retry, onBack = onBack)
}
