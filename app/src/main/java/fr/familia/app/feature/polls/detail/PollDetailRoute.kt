package fr.familia.app.feature.polls.detail

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
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.pollShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects a poll to its ViewModel, and goes back once it is deleted. */
@Composable
fun PollDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    pollId: String,
    onOpenEvent: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PollDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PollDetailViewModel(container?.pollRepository, container?.memberRepository, container?.eventRepository, currentMember.id, pollId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is PollDetailUiState.Success) wasShown = true
        if (state == PollDetailUiState.Missing && wasShown) onBack()
    }
    val context = LocalContext.current
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val shown = state as? PollDetailUiState.Success
    PollDetailScreen(
        state = state,
        onShare = shown?.takeIf { shareOn }?.let { success -> { share(context, pollShare(context, success.poll)) } },
        onAnswer = viewModel::setAnswer,
        onToggleChoice = viewModel::toggleChoice,
        onSend = viewModel::send,
        onClose = viewModel::close,
        onChooseDate = viewModel::chooseDate,
        onDelete = viewModel::delete,
        onOpenEvent = onOpenEvent,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}
