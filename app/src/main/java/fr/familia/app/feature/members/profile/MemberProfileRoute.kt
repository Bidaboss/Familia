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
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.wishes.BirthdayCardsSection
import fr.familia.app.feature.souvenirs.SouvenirCelebrationPreferences
import java.time.LocalDate
import java.time.ZoneId

/** Connects a member's page to its ViewModels. */
@Composable
fun MemberProfileRoute(
    container: AppContainer?,
    currentMember: Member,
    memberId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    /** « Voir ses recettes » (récit 38), when the recipe book is on. */
    onOpenRecipes: (() -> Unit)? = null,
    /** « Ajouter une recette » on my own page (DESIGN-172), when the recipe book is on. */
    onAddRecipe: (() -> Unit)? = null,
    /** What the Cadeaux section of the page opens. */
    giftActions: ProfileGiftActions = ProfileGiftActions(),
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
    val flags = LocalFeatureFlags.current
    val wishesOn = flags.isOn(Feature.BirthdayWishes)
    val cardOn = flags.isOn(Feature.MemberCard)
    val giftsOn = flags.isOn(Feature.Gifts)
    val genealogyOn = flags.isOn(Feature.Genealogy)
    val goodNewsOn = flags.isOn(Feature.GoodNews)
    val recipesOn = onOpenRecipes != null
    val v2Model: MemberProfileV2ViewModel = viewModel(
        key = "profile-v2-$memberId-$genealogyOn-$goodNewsOn-$cardOn-$giftsOn-$recipesOn",
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
                    recipes = container?.recipeRepository?.takeIf { recipesOn },
                    photos = container?.memberPhotoRepository,
                )
            }
        },
    )
    val v2State by v2Model.state.collectAsStateWithLifecycle()
    val viewingAs = LocalViewAs.current.viewed != null
    MemberProfileScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDismissBravo = viewModel::dismissBravo,
        wishes = if (wishesOn) ({ person -> BirthdayCardsSection(container, currentMember, person) }) else null,
        v2 = ProfileV2Ui(
            v2State,
            onOpenEvent = onOpenEvent,
            onSavePhone = v2Model::savePhone,
            onSaveAddress = v2Model::saveAddress,
            onSavePhoto = v2Model::savePhoto,
            onRemovePhoto = v2Model::removePhoto,
            onSaveCard = v2Model::saveCard,
            onOpenWishes = if (giftsOn) ({ giftActions.onOpenList(wishesListId(memberId)) }) else null,
            onPrepareSurprise = if (giftsOn && !viewingAs && memberId != currentMember.id) ({ giftActions.onPrepareSurprise(memberId) }) else null,
            onOpenRecipes = onOpenRecipes,
            onAddRecipe = onAddRecipe?.takeIf { memberId == currentMember.id },
        ),
    )
}

/** Where the Cadeaux section of a page leads: the wishes, and a new surprise for them. */
data class ProfileGiftActions(
    val onOpenList: (listId: String) -> Unit = {},
    val onPrepareSurprise: (beneficiaryId: String) -> Unit = {},
)
