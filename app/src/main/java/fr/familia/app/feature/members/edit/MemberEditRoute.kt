package fr.familia.app.feature.members.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the member form to its ViewModel, and closes it once saved. */
@Composable
fun MemberEditRoute(container: AppContainer?, currentMember: Member, memberId: String?, onDone: () -> Unit) {
    val viewModel: MemberEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MemberEditViewModel(container?.memberRepository, currentMember, memberId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == MemberEditUiState.Saved) onDone()
    }
    MemberEditScreen(
        state = state,
        onChange = viewModel::update,
        onAddHousehold = viewModel::addHousehold,
        onSave = viewModel::save,
        onBack = onDone,
    )
}
