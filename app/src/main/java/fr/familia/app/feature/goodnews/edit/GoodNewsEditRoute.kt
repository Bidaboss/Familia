package fr.familia.app.feature.goodnews.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.FamiliaApplication
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.flowOf

/** Connects the publishing form to its ViewModel, and closes it once published. */
@Composable
fun GoodNewsEditRoute(
    container: AppContainer?,
    currentMember: Member,
    postId: String?,
    onDone: () -> Unit,
    /** From « Participer au défi » (récit 35 bis): linked to the challenge of the foyer from the start. */
    forChallenge: Boolean = false,
    onBack: () -> Unit,
) {
    val online = (LocalContext.current.applicationContext as? FamiliaApplication)?.networkMonitor?.isOnline ?: flowOf(true)
    val viewModel: GoodNewsEditViewModel = viewModel(
        key = "goodNewsEdit-$postId-$forChallenge",
        factory = viewModelFactory {
            initializer {
                GoodNewsEditViewModel(
                    container?.goodNewsRepository, container?.memberRepository, container?.photoUploader,
                    currentMember.id, postId, online,
                    tree = container?.treeRepository, linkToChallenge = forChallenge,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == GoodNewsEditUiState.Done) onDone() }
    GoodNewsEditScreen(
        state = state,
        onText = viewModel::setText,
        onAddPhotos = viewModel::addPhotos,
        onRemovePhoto = viewModel::removePhoto,
        onFor = viewModel::setFor,
        onToggleMention = viewModel::toggleMention,
        onForChallenge = viewModel::setForChallenge,
        onSave = viewModel::save,
        onDismissFailure = viewModel::dismissFailure,
        onBack = onBack,
    )
}
