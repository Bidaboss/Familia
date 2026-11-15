package fr.familia.app.feature.helprequests.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the « Coups de main » tab to its ViewModel. */
@Composable
fun HelpRequestsRoute(
    container: AppContainer?,
    currentMember: Member,
    onAsk: () -> Unit,
    onOpenRequest: (String) -> Unit,
) {
    val viewModel: HelpRequestsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HelpRequestsViewModel(container?.helpRequestRepository, container?.memberRepository, currentMember.id)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HelpRequestsScreen(state = state, onAsk = onAsk, onOpenRequest = onOpenRequest, onRetry = viewModel::retry)
}
