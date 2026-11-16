package fr.familia.app.feature.members.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.souvenirs.SouvenirCelebrationPreferences

/** Connects a member's page to its ViewModel. */
@Composable
fun MemberProfileRoute(
    container: AppContainer?,
    currentMember: Member,
    memberId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    /** « Voir ses recettes » (récit 38), when the recipe book is on. */
    onOpenRecipes: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: MemberProfileViewModel = viewModel(
        key = "profile-$memberId",
        factory = viewModelFactory {
            initializer {
                MemberProfileViewModel(
                    container?.memberRepository, container?.souvenirRepository, currentMember.id, memberId,
                    SouvenirCelebrationPreferences(context),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MemberProfileScreen(state = state, onBack = onBack, onEdit = onEdit, onSetShown = viewModel::setShown, onDismissBravo = viewModel::dismissBravo, onOpenRecipes = onOpenRecipes)
}
