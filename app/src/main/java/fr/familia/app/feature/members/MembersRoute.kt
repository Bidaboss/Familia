package fr.familia.app.feature.members

import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
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
    onOpenWishes: ((memberId: String) -> Unit)?,
    /** The recipe book, while its module is on (récit 38). */
    onOpenRecipes: (() -> Unit)? = null,
    /** The time capsules, while their module is on (récit 39). */
    onOpenCapsules: (() -> Unit)? = null,
    /** The family tree, while its module is on (récit 40). */
    onOpenGenealogy: (() -> Unit)? = null,
    /** « Qui est-ce ? », while its module is on (récit 43). */
    onOpenWhoIsIt: (() -> Unit)? = null,
) {
    val viewModel: MembersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MembersViewModel(container?.memberRepository, currentMember.id, container?.memberPhotoRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val renameFailed by viewModel.renameFailed.collectAsStateWithLifecycle()
    val photoFailed by viewModel.photoFailed.collectAsStateWithLifecycle()
    MembersScreen(
        state = state,
        onRetry = viewModel::retry,
        onOpenRecipes = onOpenRecipes,
        onOpenCapsules = onOpenCapsules,
        onOpenGenealogy = onOpenGenealogy,
        onOpenWhoIsIt = onOpenWhoIsIt,
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
        r1 = LocalFeatureFlags.current.isOn(Feature.PresentationR1bis),
        // Off for everyone, the admin principal too, until switched on in Administration.
        byHousehold = LocalFeatureFlags.current.isOnForFamily(Feature.FamilyTabV2),
    )
}
