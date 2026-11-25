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
    /** While the Souvenirs module is on, a row opens the member's page (récit 34). */
    onOpenProfile: ((memberId: String) -> Unit)? = null,
    /** The « Arbre » tab, while the module Arbre is on (brief Famille-recettes, règle 1). */
    tree: (@Composable () -> Unit)? = null,
    /** ⚙ Gérer (F5): the screen checks again that the reader is the admin principal. */
    onManage: (() -> Unit)? = null,
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
        onEditMember = onEditMember,
        onOpenProfile = onOpenProfile,
        onRenameHousehold = viewModel::renameHousehold,
        renameFailed = renameFailed,
        onRenameFailureShown = viewModel::renameFailureShown,
        tree = tree,
        onManage = onManage,
    )
}
