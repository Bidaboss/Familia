package fr.familia.app.feature.gifts.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.viewas.LocalViewAs
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.core.model.Feature
import fr.familia.app.core.share.share
import fr.familia.app.core.share.wishesShare
import fr.familia.app.core.ui.LocalFeatureFlags
import androidx.compose.runtime.remember
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.feature.gifts.GiftSeenPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Connects a gift list to its ViewModel. */
@Composable
fun GiftListRoute(
    container: AppContainer?,
    currentMember: Member,
    listId: String,
    onEditList: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (itemId: String) -> Unit,
    onBack: () -> Unit,
    /** « Cadeaux communs » (récit 73): the list where a shared wish lives. */
    onOpenList: (listId: String) -> Unit = {},
    /** The occasion of a reservation of « Mes envies » is shown and chosen only with the reveal on. */
    showClaimOccasion: Boolean = false,
    /** The beneficiary's page, for their sizes and tastes (récit 7 bis). */
    onOpenBeneficiary: ((memberId: String) -> Unit)? = null,
    /** « Offrir cette envie »: the surprise I am in for the person. */
    onOpenSurprise: (surpriseId: String) -> Unit = {},
    /** « Offrir cette envie » without a surprise yet: start one for the person (or join theirs). */
    onPrepareSurprise: (beneficiaryId: String) -> Unit = {},
    /** « + Ajouter une idée » for the person (correctif E4); null while the notebook is off. */
    onAddIdea: ((personId: String) -> Unit)? = null,
    /** One of my private ideas, opened in my notebook. */
    onOpenIdea: (entryId: String) -> Unit = {},
) {
    // « Voir comme »: the admin principal still reads with their own account.
    val readerId = LocalViewAs.current.readerId ?: currentMember.id
    // « Déjà acheté » (récit 68): for the whole family or nobody, not the admin principal first.
    val alreadyBought = LocalFeatureFlags.current.isOnForFamily(Feature.AlreadyBought)
    val viewModel: GiftListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftListViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id, listId,
                    readerId = readerId,
                    alreadyBought = alreadyBought,
                    surprises = container?.surpriseRepository,
                    surprisesOnly = true,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // « Voir comme »: the admin principal never sends a list out under someone else's name.
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share) && readerId == currentMember.id
    val shown = state as? GiftListUiState.Success
    // « Mes idées » (correctif E4): my private ideas for the person, from my notebook; never in « Voir comme ».
    val ideasOn = onAddIdea != null && readerId == currentMember.id && LocalViewAs.current.viewed == null
    val beneficiaryId = shown?.beneficiary?.id
    val ideas by remember(ideasOn, beneficiaryId) {
        val notebook = container?.notebookRepository
        if (!ideasOn || notebook == null || beneficiaryId == null) {
            flowOf(null)
        } else {
            notebook.observeEntries(currentMember.id).map<List<NotebookEntry>, List<NotebookEntry>?> { ideasFor(it, beneficiaryId) }.catch { emit(emptyList()) }
        }
    }.collectAsStateWithLifecycle(null)
    // « nouveau » (correctif E3): what was added since the visit before this one, on this phone.
    val since = remember(listId) { GiftSeenPreferences(context.applicationContext).previousVisit(listId) }
    val freshIds = remember(shown?.items, since) {
        freshWishIds(shown?.items?.map { it.item }.orEmpty(), since, currentMember.id)
    }
    GiftListScreen(
        state = state,
        ideas = ideas,
        freshIds = freshIds,
        actions = GiftListActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onEditList = onEditList,
            onAddItem = onAddItem,
            onEditItem = onEditItem,
            onTake = viewModel::take,
            onOpenJointGift = viewModel::openJointGift,
            onRelease = viewModel::release,
            onSetClaimOccasion = if (showClaimOccasion) viewModel::setClaimOccasion else null,
            onSetShare = viewModel::setShare,
            onLeaveShare = viewModel::leaveShare,
            onMarkReceived = viewModel::markReceived,
            onContribute = viewModel::contribute,
            onWithdrawContribution = viewModel::withdrawContribution,
            onOpenList = onOpenList,
            onOpenBeneficiary = onOpenBeneficiary,
            onOfferWish = {
                val success = state as? GiftListUiState.Success
                if (success != null) success.surpriseId?.let(onOpenSurprise) ?: onPrepareSurprise(success.beneficiary.id)
            },
            onMarkWishReceived = if (showClaimOccasion && readerId == currentMember.id) viewModel::askWishReceived else null,
            onAddIdea = onAddIdea?.let { add -> shown?.let { success -> { add(success.beneficiary.id) } } },
            onOpenIdea = onOpenIdea,
            onSetWishOccasion = viewModel::setWishOccasion,
            onRemoveWish = viewModel::removeWish,
            onShareOutside = shown?.takeIf { shareOn && it.canSendOutside }?.let { success ->
                { share(context, wishesShare(context, success.list.id, success.beneficiary.firstName)) }
            },
        ),
    )
    val toConfirm by viewModel.receivedToConfirm.collectAsStateWithLifecycle()
    if (toConfirm != null) {
        WishReceivedDialog(onConfirm = viewModel::confirmWishReceived, onDismiss = viewModel::cancelWishReceived)
    }
}

/** My private ideas for [personId]: not moved to a list, not given yet (as « Mes idées » of a surprise). */
internal fun ideasFor(entries: List<NotebookEntry>, personId: String): List<NotebookEntry> =
    entries.filter { it.source == NotebookSource.Idea && it.personId == personId && it.movedTo == null && it.status != GiftStatus.Given }
