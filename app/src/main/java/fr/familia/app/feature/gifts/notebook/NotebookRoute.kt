package fr.familia.app.feature.gifts.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.NotebookOccasion

/** Connects « Mes cadeaux à offrir » to its ViewModel. */
@Composable
fun NotebookRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    onOpenList: (listId: String) -> Unit,
    onAddIdea: (at: NotebookOccasion?) -> Unit,
    onEditIdea: (entryId: String) -> Unit,
    onProposeIdea: (entryId: String) -> Unit,
) {
    val viewModel: NotebookViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotebookViewModel(container?.notebookRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotebookScreen(
        state = state,
        actions = NotebookActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onSelect = viewModel::select,
            onSetStatus = viewModel::setStatus,
            onSetOccasion = viewModel::setOccasion,
            onSetBudget = viewModel::setBudget,
            onOpenList = onOpenList,
            onAddIdea = onAddIdea,
            onEditIdea = onEditIdea,
            onProposeIdea = onProposeIdea,
        ),
    )
}
