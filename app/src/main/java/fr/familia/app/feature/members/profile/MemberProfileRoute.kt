package fr.familia.app.feature.members.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects a member's page to its ViewModel. */
@Composable
fun MemberProfileRoute(container: AppContainer?, currentMember: Member, memberId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val viewModel: MemberProfileViewModel = viewModel(
        key = "profile-$memberId",
        factory = viewModelFactory {
            initializer { MemberProfileViewModel(container?.memberRepository, container?.souvenirRepository, currentMember.id, memberId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MemberProfileScreen(state = state, onBack = onBack, onEdit = onEdit, onSetShown = viewModel::setShown)
}
