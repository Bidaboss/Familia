package fr.familia.app.feature.turns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** « À qui le tour ? » in Entraide (PRD, récit 58), shown under its tab. */
@Composable
fun TurnsRoute(
    container: AppContainer?,
    currentMember: Member,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TurnsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TurnsViewModel(container?.turnRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TurnsScreen(state, onCreate, onOpen, viewModel::retry, modifier)
}

@Composable
fun TurnEditRoute(
    container: AppContainer?,
    currentMember: Member,
    turnId: String?,
    onDone: () -> Unit,
) {
    val viewModel: TurnEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TurnEditViewModel(container?.turnRepository, container?.memberRepository, currentMember.id, turnId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == TurnEditUiState.Done) onDone()
    }
    TurnEditScreen(
        state,
        onTitle = viewModel::setTitle,
        onToggle = viewModel::toggle,
        onUp = viewModel::moveUp,
        onDown = viewModel::moveDown,
        onSave = viewModel::save,
        onDismissFailure = viewModel::dismissFailure,
        onBack = onDone,
    )
}
