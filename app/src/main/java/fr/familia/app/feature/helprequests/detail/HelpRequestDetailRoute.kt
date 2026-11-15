package fr.familia.app.feature.helprequests.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects a request to its ViewModel, and goes back once it is cancelled or deleted. */
@Composable
fun HelpRequestDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    requestId: String,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: HelpRequestDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HelpRequestDetailViewModel(container?.helpRequestRepository, container?.memberRepository, currentMember.id, requestId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is HelpRequestDetailUiState.Success) wasShown = true
        if (state == HelpRequestDetailUiState.Missing && wasShown) onBack()
    }
    HelpRequestDetailScreen(
        state = state,
        onTake = viewModel::take,
        onDecline = viewModel::decline,
        onWithdraw = viewModel::withdraw,
        onThank = viewModel::thank,
        onCancel = viewModel::cancel,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}
