package fr.familia.app.feature.members.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.members.card.MemberCardSection
import fr.familia.app.feature.thinkofme.ThinkOfMeSection
import fr.familia.app.feature.wishes.BirthdayCardsSection
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
    val wishesOn = LocalFeatureFlags.current.isOn(Feature.BirthdayWishes)
    val cardOn = LocalFeatureFlags.current.isOn(Feature.MemberCard)
    val thinkOfMeOn = LocalFeatureFlags.current.isOn(Feature.ThinkOfMe)
    MemberProfileScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onSetShown = viewModel::setShown,
        onDismissBravo = viewModel::dismissBravo,
        onOpenRecipes = onOpenRecipes,
        wishes = if (wishesOn) ({ person -> BirthdayCardsSection(container, currentMember, person) }) else null,
        card = if (cardOn) ({ person -> MemberCardSection(container, currentMember, person) }) else null,
        thinkOfMe = if (thinkOfMeOn) ({ person -> ThinkOfMeSection(container, currentMember, person) }) else null,
    )
}
