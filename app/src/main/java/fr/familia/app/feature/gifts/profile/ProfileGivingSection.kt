package fr.familia.app.feature.gifts.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.gifts.keepsListsOf
import fr.familia.app.feature.gifts.notebook.GiftRow
import fr.familia.app.feature.gifts.notebook.NotebookActions
import fr.familia.app.feature.gifts.notebook.NotebookUiState
import fr.familia.app.feature.gifts.notebook.NotebookViewModel
import fr.familia.app.feature.gifts.notebook.StatusSheet
import fr.familia.app.feature.gifts.notebook.giftsFor
import fr.familia.app.feature.presentation.LocalPresenting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What I give to one member: my notebook for them, and the surprises I see being prepared. */
data class GivingState(
    val entries: List<NotebookEntry>,
    val surprises: List<GiftList>,
    /** With the single list: their ideas of the family hold a surprise idea, kept from them. */
    val surpriseIdea: Boolean = false,
)

/**
 * « Ce que je lui offre » on the page of [memberId] (retour d'essai, 17/11): my reservations, my
 * parts and my private ideas for them, from my notebook, and the surprises in progress for them.
 * Only mine: the notebook is private, and the page never asks for anyone else's.
 */
class ProfileGivingViewModel(
    notebook: NotebookRepository?,
    gifts: GiftRepository?,
    currentMemberId: String,
    memberId: String,
    singleList: Boolean,
    /** Off with the notebook module: only the surprises then. */
    notebookOn: Boolean,
    /** Their ideas are kept from me (my own child without the app): nothing to read there. */
    keptByMe: Boolean = false,
) : ViewModel() {
    private val entries = (notebook?.takeIf { notebookOn }?.observeEntries(currentMemberId) ?: flowOf(emptyList()))
        .map { giftsFor(it, memberId, singleList) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }

    private val surprises = (gifts?.observeVisibleLists(currentMemberId) ?: flowOf(emptyList()))
        .map { preparedSurprises(it, memberId) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }

    // With the single list a surprise is an idea of the family (retour d'essai du 17/11).
    private val surpriseIdea = (gifts?.takeIf { singleList && !keptByMe }?.observeItems(ideasListId(memberId), hiddenFromMe = false) ?: flowOf(emptyList()))
        .map { items -> items.any { !it.visibleToBeneficiary } }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(false)
        }

    val state: StateFlow<GivingState?> = combine(entries, surprises, surpriseIdea) { e, s, i -> GivingState(e, s, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The surprises for [memberId] not revealed yet, the ones I can see. */
internal fun preparedSurprises(lists: List<GiftList>, memberId: String): List<GiftList> =
    lists.filter { it.kind == GiftListKind.Surprise && !it.revealed && it.beneficiaryId == memberId }

/**
 * Never on one's own page, never in « Voir comme » (the notebook is never shown there, PRD), never
 * in presentation mode: the page may be on screen in front of the person.
 */
internal fun showsGiving(currentMemberId: String, memberId: String, viewingAs: Boolean, presenting: Boolean): Boolean =
    currentMemberId != memberId && !viewingAs && !presenting

@Composable
fun ProfileGivingSection(
    container: AppContainer?,
    currentMember: Member,
    member: Member,
    singleList: Boolean,
    notebookOn: Boolean,
    onOpenList: (listId: String) -> Unit,
    onOpenNotebook: () -> Unit,
    onAddIdea: (personId: String) -> Unit,
    onEditIdea: (entryId: String) -> Unit,
    onProposeIdea: (entryId: String) -> Unit,
) {
    if (!showsGiving(currentMember.id, member.id, LocalViewAs.current.viewed != null, LocalPresenting.current)) return
    val viewModel: ProfileGivingViewModel = viewModel(
        key = "profile-giving-${member.id}-$singleList-$notebookOn",
        factory = viewModelFactory {
            initializer {
                ProfileGivingViewModel(
                    container?.notebookRepository, container?.giftRepository, currentMember.id, member.id, singleList, notebookOn,
                    keptByMe = keepsListsOf(currentMember, member),
                )
            }
        },
    )
    // The writes go through the notebook itself: the reveal of a reservation follows its occasion.
    val notebook: NotebookViewModel = viewModel(
        key = "profile-notebook-$singleList",
        factory = viewModelFactory {
            initializer {
                NotebookViewModel(container?.notebookRepository, currentMember.id, gifts = container?.giftRepository, singleList = singleList)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notebookState by notebook.uiState.collectAsStateWithLifecycle()
    val failed = (notebookState as? NotebookUiState.Success)?.actionFailed == true
    val shown = state ?: return
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val actions = remember(notebook) {
        NotebookActions(
            onSetStatus = notebook::setStatus,
            onSetOccasion = notebook::setOccasion,
            onOpenList = onOpenList,
            onEditIdea = onEditIdea,
            onProposeIdea = onProposeIdea,
        )
    }
    shown.entries.firstOrNull { it.id == openEntryId }?.let { entry ->
        StatusSheet(entry, actions, onDismiss = { openEntryId = null })
    }
    ProfileGivingContent(
        state = shown,
        member = member,
        notebookOn = notebookOn,
        failed = failed,
        onOpenEntry = { openEntryId = it.id },
        onOpenList = onOpenList,
        onOpenNotebook = onOpenNotebook,
        onAddIdea = { onAddIdea(member.id) },
    )
}

/** The card of the page. Stateless. */
@Composable
fun ProfileGivingContent(
    state: GivingState,
    member: Member,
    notebookOn: Boolean,
    failed: Boolean,
    onOpenEntry: (NotebookEntry) -> Unit,
    onOpenList: (String) -> Unit,
    onOpenNotebook: () -> Unit,
    onAddIdea: () -> Unit,
) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Household)
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Household, contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClayCardTitle(stringResource(R.string.profile_giving_title, member.firstName))
            Text(stringResource(R.string.notebook_private), style = MaterialTheme.typography.bodyMedium, color = variant)
            if (failed) {
                Text(
                    stringResource(R.string.gift_action_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.entries.isEmpty() && state.surprises.isEmpty() && !state.surpriseIdea) {
                Text(stringResource(R.string.profile_giving_empty), style = MaterialTheme.typography.bodyLarge)
            }
            state.entries.forEach { entry -> GiftRow(entry, variant) { onOpenEntry(entry) } }
            state.surprises.forEach { list ->
                ClayLink(stringResource(R.string.profile_giving_surprise, list.title), onClick = { onOpenList(list.id) })
            }
            if (state.surpriseIdea) {
                ClayLink(stringResource(R.string.gifts_surprise_prepared), onClick = { onOpenList(ideasListId(member.id)) })
            }
            if (notebookOn) {
                ClayLink(stringResource(R.string.notebook_add_idea), onClick = onAddIdea)
                ClayLink(stringResource(R.string.profile_giving_all), onClick = onOpenNotebook)
            }
        }
    }
}
