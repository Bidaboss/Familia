package fr.familia.app.feature.gifts.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects « Proposer à la famille » to its ViewModel; [onDone] opens the list the idea went to. */
@Composable
fun ProposeIdeaRoute(
    container: AppContainer?,
    currentMember: Member,
    entryId: String,
    onBack: () -> Unit,
    onDone: (listId: String) -> Unit,
) {
    val viewModel: ProposeIdeaViewModel = viewModel(
        key = "propose-$entryId",
        factory = viewModelFactory {
            initializer {
                ProposeIdeaViewModel(
                    container?.notebookRepository, container?.giftRepository, container?.memberRepository, currentMember.id, entryId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        (state as? ProposeIdeaUiState.Done)?.let { onDone(it.listId) }
    }
    ProposeIdeaScreen(
        state = state,
        actions = ProposeIdeaActions(
            onBack = onBack,
            onSelectList = viewModel::selectList,
            onTakeIt = viewModel::setTakeIt,
            onPropose = viewModel::propose,
        ),
    )
}
