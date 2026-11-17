package fr.familia.app.feature.helprequests.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.SinglePhotoSaver

/** Connects the request form to its ViewModel, and closes it once saved. */
@Composable
fun HelpRequestEditRoute(
    container: AppContainer?,
    currentMember: Member,
    requestId: String?,
    duplicateOf: String?,
    onDone: () -> Unit,
) {
    val uploader = container?.photoUploader
    val storage = container?.photoStorage
    val photos = if (uploader != null && storage != null) SinglePhotoSaver(uploader, storage) else null
    val viewModel: HelpRequestEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HelpRequestEditViewModel(
                    container?.helpRequestRepository,
                    container?.memberRepository,
                    currentMember.id,
                    requestId = requestId,
                    duplicateOf = duplicateOf,
                    photos = photos,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == HelpRequestEditUiState.Done) onDone()
    }
    HelpRequestEditScreen(
        state = state,
        onChange = viewModel::update,
        onToggle = viewModel::toggle,
        onShortcut = viewModel::applyShortcut,
        onSave = viewModel::save,
        onBack = onDone,
        photoOn = photos != null,
    )
}
