package fr.familia.app.feature.joinme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.place.AndroidPlaceLocator

/**
 * Connects « Rejoignez-moi » to its ViewModel. The location permission is asked « pendant
 * l'utilisation de l'appli », the first time the member wants their position, never before.
 */
@Composable
fun JoinMeRoute(container: AppContainer?, currentMember: Member, onDone: () -> Unit) {
    val viewModel: JoinMeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { JoinMeViewModel(container?.memberRepository, container?.joinMeRepository, container?.placeLocator, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.locate() else viewModel.permissionDenied()
    }
    val locateHere = {
        if (container?.placeLocator?.hasPermission() == true) viewModel.locate() else permission.launch(AndroidPlaceLocator.PERMISSIONS)
    }
    LaunchedEffect(state) {
        if (state == JoinMeUiState.Sent) onDone()
    }
    JoinMeScreen(
        state = state,
        onLocate = locateHere,
        onSource = viewModel::chooseSource,
        onQuery = viewModel::changeQuery,
        onSearch = viewModel::search,
        onName = viewModel::changeName,
        onNote = viewModel::changeNote,
        onToggle = viewModel::toggle,
        onShortcut = viewModel::applyShortcut,
        onSend = viewModel::send,
        onBack = onDone,
    )
}
