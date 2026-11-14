package fr.familia.app.feature.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the Famille tab to its ViewModel. */
@Composable
fun MembersRoute(
    container: AppContainer?,
    currentMember: Member,
    onEditMember: (memberId: String?) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val viewModel: MembersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MembersViewModel(container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val renameFailed by viewModel.renameFailed.collectAsStateWithLifecycle()
    MembersScreen(
        state = state,
        onRetry = viewModel::retry,
        onOpenNotifications = onOpenNotifications,
        onOpenUpdates = onOpenUpdates,
        onOpenHelp = onOpenHelp,
        // The backup holds everyone's data: admins only (PRD, récit 6 ter).
        onOpenExport = if ((state as? MembersUiState.Success)?.isAdmin == true) onOpenExport else null,
        // Only admins add and edit members (the security rules check it too).
        onEditMember = if ((state as? MembersUiState.Success)?.isAdmin == true) onEditMember else null,
        // Any admin renames any household (data-model.md, « Droits des admins »).
        onRenameHousehold = if ((state as? MembersUiState.Success)?.isAdmin == true) viewModel::renameHousehold else null,
        renameFailed = renameFailed,
        onRenameFailureShown = viewModel::renameFailureShown,
    )
}
