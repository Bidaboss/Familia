package fr.familia.app.feature.gifts.surprise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/**
 * Connects « Nouvelle surprise » ([beneficiaryId]) or « Modifier la surprise » ([surpriseId]) to its
 * ViewModel; [onDone] gets the surprise to show next, null once deleted.
 */
@Composable
fun SurpriseEditRoute(
    container: AppContainer?,
    currentMember: Member,
    beneficiaryId: String?,
    surpriseId: String?,
    onDone: (surpriseId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SurpriseEditViewModel = viewModel(
        key = "surprise-edit-$beneficiaryId-$surpriseId",
        factory = viewModelFactory {
            initializer {
                SurpriseEditViewModel(container?.surpriseRepository, container?.memberRepository, currentMember.id, beneficiaryId, surpriseId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        (state as? SurpriseEditUiState.Done)?.let { onDone(it.surpriseId) }
    }
    SurpriseEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onJoin = viewModel::join,
        onOpen = viewModel::open,
        onBack = onBack,
    )
}
