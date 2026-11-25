package fr.familia.app.feature.members.manage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.members.MembersViewModel
import fr.familia.app.feature.members.edit.shareInvitation
import kotlinx.serialization.Serializable

/** Famille > ⚙ Gérer (brief Famille-recettes F5), the admin principal only. */
@Serializable
data object ManageFamilyDestination

@Composable
fun ManageFamilyRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    /** The member form: a new member with null. */
    onEditMember: (memberId: String?) -> Unit,
) {
    val viewModel: MembersViewModel = viewModel(
        factory = viewModelFactory { initializer { MembersViewModel(container?.memberRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ManageFamilyScreen(
        state = state,
        onBack = onBack,
        onAdd = { onEditMember(null) },
        onEdit = { id -> onEditMember(id) },
        onRemind = { member -> member.email?.let { runCatching { shareInvitation(context, member.firstName, it) } } },
    )
}
