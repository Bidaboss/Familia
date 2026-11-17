package fr.familia.app.feature.gifts.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.ui.LocalFeatureFlags

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
    onAddPurchase: (at: NotebookOccasion) -> Unit = {},
) {
    val singleList = LocalFeatureFlags.current.isOn(Feature.SingleList)
    // « Mon budget » (récit 57): adults only, the children do not have it.
    val budget = LocalFeatureFlags.current.isOn(Feature.GiftBudget) && currentMember.profile == MemberProfile.Adult
    val viewModel: NotebookViewModel = viewModel(
        key = "notebook-$singleList-$budget",
        factory = viewModelFactory {
            initializer {
                NotebookViewModel(
                    container?.notebookRepository, currentMember.id, gifts = container?.giftRepository, singleList = singleList,
                    occasionBudget = budget,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotebookScreen(
        state = state,
        actions = NotebookActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onSelect = viewModel::select,
            onToggleArchive = viewModel::toggleArchive,
            onSetStatus = viewModel::setStatus,
            onSetOccasion = viewModel::setOccasion,
            onSetBudget = viewModel::setBudget,
            onOpenList = onOpenList,
            onAddIdea = onAddIdea,
            onEditIdea = onEditIdea,
            onProposeIdea = onProposeIdea,
            onSetOccasionBudget = viewModel::setOccasionBudget,
            onAddPurchase = onAddPurchase,
        ),
    )
}
