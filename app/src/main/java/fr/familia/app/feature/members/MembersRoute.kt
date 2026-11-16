package fr.familia.app.feature.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.shareApp
import fr.familia.app.core.viewas.LocalViewAs

/** Connects the Famille tab to its ViewModel. */
@Composable
fun MembersRoute(
    container: AppContainer?,
    currentMember: Member,
    onEditMember: (memberId: String?) -> Unit,
    /** While the Souvenirs module is on, a row opens the member's page (récit 34). */
    onOpenProfile: ((memberId: String) -> Unit)? = null,
    onOpenNotifications: () -> Unit,
    onOpenAdministration: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWishes: ((memberId: String) -> Unit)?,
    /** Shown while the Thèmes module is on. */
    onOpenAppearance: (() -> Unit)? = null,
    /** The time of one's children in the app, while the Utilisation module is on (carte EXPERT-5). */
    onOpenChildrenUsage: (() -> Unit)? = null,
    /** The recipe book, while its module is on (récit 38). */
    onOpenRecipes: (() -> Unit)? = null,
    /** The time capsules, while their module is on (récit 39). */
    onOpenCapsules: (() -> Unit)? = null,
    /** The family tree, while its module is on (récit 40). */
    onOpenGenealogy: (() -> Unit)? = null,
    /** « Une idée ? Un souci ? » (récit 6 sexies). */
    onOpenFeedback: (() -> Unit)? = null,
) {
    val viewModel: MembersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MembersViewModel(container?.memberRepository, currentMember.id, container?.memberPhotoRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val renameFailed by viewModel.renameFailed.collectAsStateWithLifecycle()
    val photoFailed by viewModel.photoFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Nothing leaves the app while the admin looks through a member's eyes.
    val inViewAs = LocalViewAs.current.viewed != null
    MembersScreen(
        state = state,
        onShareApp = if (inViewAs) null else { ownerName -> shareApp(context, ownerName) },
        onOpenFeedback = onOpenFeedback?.takeIf { !inViewAs },
        onRetry = viewModel::retry,
        onOpenNotifications = onOpenNotifications,
        onOpenUpdates = onOpenUpdates,
        onOpenHelp = onOpenHelp,
        onOpenAppearance = onOpenAppearance,
        onOpenChildrenUsage = onOpenChildrenUsage,
        onOpenRecipes = onOpenRecipes,
        onOpenCapsules = onOpenCapsules,
        onOpenGenealogy = onOpenGenealogy,
        // The screen shows each action only where the live rights allow it (the rules check it too).
        onOpenAdministration = onOpenAdministration,
        onEditMember = onEditMember,
        onOpenProfile = onOpenProfile,
        onRenameHousehold = viewModel::renameHousehold,
        renameFailed = renameFailed,
        onRenameFailureShown = viewModel::renameFailureShown,
        onSavePhoto = viewModel::savePhoto,
        onRemovePhoto = viewModel::removePhoto,
        photoFailed = photoFailed,
        onPhotoFailureShown = viewModel::photoFailureShown,
        onOpenWishes = onOpenWishes,
    )
}
