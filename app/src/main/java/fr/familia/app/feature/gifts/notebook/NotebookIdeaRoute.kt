package fr.familia.app.feature.gifts.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.NotebookOccasion

/** Connects the idea form to its ViewModel; [onDone] closes it once saved or removed. */
@Composable
fun NotebookIdeaRoute(
    container: AppContainer?,
    currentMember: Member,
    entryId: String?,
    startOccasion: NotebookOccasion,
    onDone: () -> Unit,
    startBought: Boolean = false,
    startPersonId: String? = null,
    startNote: String? = null,
    /** Opened from a surprise (E13): only the words of the 🔒 change. */
    fromSurprise: Boolean = false,
) {
    val viewModel: NotebookIdeaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                NotebookIdeaViewModel(
                    container?.notebookRepository, container?.memberRepository, currentMember.id, entryId, startOccasion,
                    startBought = startBought,
                    startPersonId = startPersonId,
                    startNote = startNote,
                    saved = createSavedStateHandle(),
                    previews = container?.linkPreviewRepository,
                    fromSurprise = fromSurprise,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == NotebookIdeaUiState.Done) onDone()
    }
    NotebookIdeaScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
        onPasteLink = viewModel::pasteLink,
    )
}
