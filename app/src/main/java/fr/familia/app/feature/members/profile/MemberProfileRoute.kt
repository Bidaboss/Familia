package fr.familia.app.feature.members.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/** How long a member's page waits for all its data before showing what it has (its skeleton meanwhile). */
internal const val FICHE_WAIT_MS = 1_500L

/** Connects a member's page to its ViewModels. */
@Composable
fun MemberProfileRoute(
    container: AppContainer?,
    currentMember: Member,
    memberId: String,
    onBack: () -> Unit,
    /** Opened from « Modifier sa fiche » (Gérer, the tree): the page starts in « Modifier ». */
    startEditing: Boolean = false,
    /** The admin removed this member from their page: it closes, with « X est retiré de Familia… ». */
    onRemoved: (firstName: String) -> Unit = {},
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
                    notes = container?.memberNoteRepository,
                )
            }
        },
    )
    val v2State by v2Model.state.collectAsStateWithLifecycle()
    val edit by v2Model.edit.collectAsStateWithLifecycle()
    val removed by v2Model.removed.collectAsStateWithLifecycle()
    val viewingAs = LocalViewAs.current.viewed != null
    // « Modifier » in place (règle 3): on my own page (F10), on a child's page for the adults who look after
    // them (précision de C2, 25/11), and on every page for the admin principal (F9).
    val ready = state as? MemberProfileUiState.Ready
    val mayEdit = !viewingAs && ready != null && (memberId == currentMember.id || ready.canEdit || v2State?.canEdit == true)
    // The page shows whole or not at all (retour de l'admin, 25/11): its skeleton stays while the second half of its
    // data comes, at most FICHE_WAIT_MS, never a first page without its tiles then the full one.
    var waited by remember(memberId) { mutableStateOf(false) }
    LaunchedEffect(memberId) { delay(FICHE_WAIT_MS); waited = true }
    val shown = when {
        ready == null -> state
        v2State == null && !waited -> MemberProfileUiState.Loading
        else -> ready.copy(canEdit = mayEdit)
    }
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mayEdit, v2State != null) {
        if (startEditing && !started && mayEdit && v2State != null) {
            started = true
            v2Model.startEdit()
        }
    }
    LaunchedEffect(removed) { removed?.let(onRemoved) }
    MemberProfileScreen(
        state = shown,
        onBack = onBack,
        onEdit = v2Model::startEdit,
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
            onRenameHousehold = v2Model::renameHousehold,
            onEditNote = v2Model::editNote,
            onMakeIdea = if (giftsOn && !viewingAs) ({ note -> giftActions.onMakeIdea(memberId, note) }) else null,
            edit = edit?.takeIf { mayEdit },
            onStartEdit = if (mayEdit) v2Model::startEdit else null,
            onDraft = v2Model::updateDraft,
            onCancelEdit = v2Model::cancelEdit,
            onSaveEdit = v2Model::saveEdit,
            onAddHousehold = v2Model::addHousehold,
            onRemove = v2Model::removeMember,
        ),
    )
}

/** Where the Cadeaux section of a page leads: the wishes, and a new surprise for them. */
data class ProfileGiftActions(
    val onOpenList: (listId: String) -> Unit = {},
    val onPrepareSurprise: (beneficiaryId: String) -> Unit = {},
    /** « En faire une idée » from « Mes notes »: a new idea of the carnet about this member. */
    val onMakeIdea: (personId: String, note: String) -> Unit = { _, _ -> },
)
