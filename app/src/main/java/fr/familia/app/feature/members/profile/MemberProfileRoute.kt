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
import fr.familia.app.feature.gifts.profile.ProfileGivingSection
import fr.familia.app.feature.gifts.profile.ProfileNotebookSection
import fr.familia.app.feature.gifts.profile.ProfileWishesSection
import fr.familia.app.feature.members.card.MemberCardSection
import fr.familia.app.feature.wishes.BirthdayCardsSection
import fr.familia.app.feature.souvenirs.SouvenirCelebrationPreferences
import java.time.LocalDate
import java.time.ZoneId

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
    /** What the Cadeaux sections of the page open (retour d'essai, 17/11). */
    giftActions: ProfileGiftActions = ProfileGiftActions(),
    /** The new page (récit 66): « Sa famille » opens a page, « Ses moments » an event. */
    onOpenMember: (memberId: String) -> Unit = {},
    onOpenEvent: (eventId: String) -> Unit = {},
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
    val giftsOn = LocalFeatureFlags.current.isOn(Feature.Gifts)
    val notebookOn = LocalFeatureFlags.current.isOn(Feature.GiftNotebook)
    // The page organised in sections (récit 66), the only one since the 18/11 (PRODUIT-131).
    val v2 = run {
        val genealogyOn = LocalFeatureFlags.current.isOn(Feature.Genealogy)
        val goodNewsOn = LocalFeatureFlags.current.isOn(Feature.GoodNews)
        val v2Model: MemberProfileV2ViewModel = viewModel(
            key = "profile-v2-$memberId-$genealogyOn-$goodNewsOn-$cardOn-$giftsOn-${onOpenRecipes != null}",
            factory = viewModelFactory {
                initializer {
                    MemberProfileV2ViewModel(
                        container?.memberRepository,
                        container?.genealogyRepository?.takeIf { genealogyOn },
                        container?.eventRepository,
                        container?.rsvpRepository,
                        container?.goodNewsRepository?.takeIf { goodNewsOn },
                        readerId = currentMember.id,
                        memberId = memberId,
                        today = LocalDate.now(ZoneId.of("Europe/Paris")),
                        memberCards = container?.memberCardRepository?.takeIf { cardOn },
                        gifts = container?.giftRepository?.takeIf { giftsOn },
                        recipes = container?.recipeRepository?.takeIf { onOpenRecipes != null },
                        photos = container?.memberPhotoRepository,
                    )
                }
            },
        )
        val v2State by v2Model.state.collectAsStateWithLifecycle()
        ProfileV2Ui(
            v2State, onOpenMember, onOpenEvent, v2Model::savePhone, v2Model::saveAddress,
            onSavePhoto = v2Model::savePhoto,
            onRemovePhoto = v2Model::removePhoto,
        )
    }
    MemberProfileScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onSetShown = viewModel::setShown,
        onDismissBravo = viewModel::dismissBravo,
        onOpenRecipes = onOpenRecipes,
        wishes = if (wishesOn) ({ person -> BirthdayCardsSection(container, currentMember, person) }) else null,
        card = if (cardOn) ({ person -> MemberCardSection(container, currentMember, person) }) else null,
        gifts = if (giftsOn) {
            { person ->
                ProfileWishesSection(container, currentMember, person, giftActions.onOpenList)
                // My private notebook, under my wishes, on my own page only (PRD rév. 293).
                ProfileNotebookSection(currentMember, person, notebookOn, giftActions.onOpenNotebook)
                ProfileGivingSection(
                    container, currentMember, person, notebookOn,
                    onOpenList = giftActions.onOpenList,
                    onOpenNotebook = giftActions.onOpenNotebook,
                    onAddIdea = giftActions.onAddIdea,
                    onEditIdea = giftActions.onEditIdea,
                    onProposeIdea = giftActions.onProposeIdea,
                )
            }
        } else {
            null
        },
        v2 = v2,
    )
}

/** Where the « Ses envies » and « Ce que je lui offre » sections of a page lead. */
data class ProfileGiftActions(
    val onOpenList: (listId: String) -> Unit = {},
    val onOpenNotebook: () -> Unit = {},
    val onAddIdea: (personId: String) -> Unit = {},
    val onEditIdea: (entryId: String) -> Unit = {},
    val onProposeIdea: (entryId: String) -> Unit = {},
)
