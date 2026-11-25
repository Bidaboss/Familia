package fr.familia.app.feature.feed.publish

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

/** Connects « Publier dans la gazette » to its ViewModel, and closes it once published. */
@Composable
fun PostEditRoute(
    container: AppContainer?,
    currentMember: Member,
    postId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val online = (LocalContext.current.applicationContext as? FamiliaApplication)?.networkMonitor?.isOnline ?: flowOf(true)
    val viewModel: PostEditViewModel = viewModel(
        key = "gazettePost-$postId",
        factory = viewModelFactory {
            initializer {
                PostEditViewModel(container?.gazettePostRepository, container?.memberRepository, container?.photoUploader, currentMember.id, postId, online)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == PostEditUiState.Done) onDone() }
    PostEditScreen(
        state = state,
        onText = viewModel::setText,
        onAddPhotos = viewModel::addPhotos,
        onRemovePhoto = viewModel::removePhoto,
        onMentions = viewModel::setMentions,
        onSave = viewModel::save,
        onDismissFailure = viewModel::dismissFailure,
        onCancelSending = viewModel::cancelSending,
        onBack = onBack,
    )
}
