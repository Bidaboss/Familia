package fr.familia.app.feature.gifts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.isPermanent
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.defaultWishOccasion
import fr.familia.app.core.model.wishesListId
import fr.familia.app.feature.gifts.canManageList
import fr.familia.app.feature.gifts.canReserve
import fr.familia.app.feature.gifts.canShare
import fr.familia.app.feature.gifts.canShareWishesOutside
import fr.familia.app.feature.gifts.isKeptFrom
import fr.familia.app.feature.gifts.keepsListsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** A reservation as the screen shows it, with the parts of a joint gift. */
data class ClaimView(
    val type: ClaimType,
    val holderName: String,
    val isMine: Boolean,
    /** Every part for the responsible; only the member's own part for the other participants. */
    val shares: List<GiftShare> = emptyList(),
    val myShare: GiftShare? = null,
    /** The total of the parts: counted live by the responsible, kept by the server for the others. */
    val total: Double = shares.sumOf { it.amount },
    /** « Mes envies » only: the occasion the gift is meant for, chosen by the holder (PRD rév. 186). */
    val occasion: GiftOccasion? = null,
)

data class ItemRow(
    val item: GiftItem,
    /** Edit and delete: the author, until it is reserved; the beneficiary's side always. */
    val canEdit: Boolean,
    /** A surprise idea of someone else: shown to the viewers with « Idée de … ». */
    val isSurpriseIdea: Boolean,
    /** Null when free, or when the reservations are hidden from this member. */
    val claim: ClaimView?,
)

/** Why a reservation did not hold, told gently rather than as a failure. */
data class ClaimLost(val itemId: String, val reason: Reason) {
    enum class Reason { Taken, Removed }
}

sealed interface GiftListUiState {
    data object Loading : GiftListUiState

    data class Success(
        val list: GiftList,
        val beneficiary: Member,
        val today: LocalDate,
        /** The member is the beneficiary and the list is not revealed: ideas and reservations stay hidden. */
        val keptFromMe: Boolean,
        val isMine: Boolean,
        val canManage: Boolean,
        val canAddItems: Boolean,
        val canReserve: Boolean,
        /** Adults only: a child takes an item alone (carte PRODUIT-19). */
        val canShare: Boolean,
        val items: List<ItemRow>,
        /** First name of the signed-in member, written with what they reserve. */
        val myName: String,
        val actionFailed: Boolean = false,
        /** A reservation refused because someone came first, or the idea left the list (passe « en même temps »). */
        val claimLost: ClaimLost? = null,
        /**
         * « Voir comme »: the admin principal, who reads with their own account, is the beneficiary of
         * this list before its reveal. The rules keep its reservations and surprise ideas from them, so
         * the list shows as they would see it, and says why.
         */
        val keptFromReader: Boolean = false,
        /** « Je l'ai reçue » on a wish of « Mes envies »: whoever keeps the person's lists (rév. 232). */
        val canMarkReceived: Boolean = false,
        /** « Partager » outside the app (récit 31): see [fr.familia.app.feature.gifts.canShareWishesOutside]. */
        val canSendOutside: Boolean = false,
    ) : GiftListUiState

    /** Deleted, or not one this member may see. */
    data object Missing : GiftListUiState

    data object Error : GiftListUiState
}

/**
 * One gift list (PRD, récits 7 to 9): its items, and for everyone but the beneficiary the
 * reservations and joint gifts. A « Mes envies » nobody wrote to yet shows empty for the member who keeps it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftListViewModel(
    private val gifts: GiftRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val listId: String,
    private val writeTimeoutMillis: Long = 4_000,
    private val today: Flow<LocalDate> = currentDayFlow(),
    /** Who really reads: the admin principal in « Voir comme », else the member themselves. */
    private val readerId: String = currentMemberId,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val failed = MutableStateFlow<Failure?>(null)

    /** The last write that did not hold: a reservation is told apart, to say why. */
    private sealed interface Failure {
        data object Write : Failure
        data class Claim(val itemId: String) : Failure
    }

    val uiState: StateFlow<GiftListUiState> = attempt
        .flatMapLatest { n ->
            val repo = gifts
            if (repo == null || members == null) {
                flowOf(GiftListUiState.Missing)
            } else {
                combine(members.observeFamily(), repo.observeList(listId)) { family, list -> family to list }
                    .flatMapLatest { (family, stored) -> content(repo, family, stored) }
                    .onStart { if (n > 0) emit(GiftListUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(GiftListUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GiftListUiState.Loading)

    private fun content(repo: GiftRepository, family: Family, stored: GiftList?): Flow<GiftListUiState> {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return flowOf(GiftListUiState.Missing)
        val list = stored ?: emptyWishes(family, me) ?: return flowOf(GiftListUiState.Missing)
        val beneficiary = family.members.firstOrNull { it.id == list.beneficiaryId } ?: return flowOf(GiftListUiState.Missing)
        val kept = isKeptFrom(list, me) || keptFromReader(list)
        val items = if (stored == null) flowOf(emptyList<GiftItem>()) else repo.observeItems(list.id, hiddenFromMe = kept)
        val claims = if (stored == null || kept) flowOf(emptyList<GiftClaim>()) else repo.observeClaims(list.id)
        val shares = claims
            .map { all ->
                val joint = all.filter { it.type == ClaimType.Shared }
                joint.map { it.itemId }.sorted() to joint.filter { it.holderId == me.id }.map { it.itemId }.toSet()
            }
            .distinctUntilChanged()
            .flatMapLatest { (ids, mine) ->
                // The parts never take the whole list down: without them, the joint gift shows no amount.
                repo.observeShares(list.id, ids, me.id, mine).catch { e ->
                    if (e is CancellationException) throw e
                    emit(emptyMap())
                }
            }
        return combine(items, claims, shares, today, failed) { itemList, claimList, shareMap, day, didFail ->
            state(list, stored != null, beneficiary, me, family, itemList, claimList, shareMap, day, didFail)
        }
    }

    /**
     * A « Mes envies » nobody created yet: shown empty to everyone. Only the member who keeps it can
     * add to it, and its first item creates it.
     */
    private fun emptyWishes(family: Family, me: Member): GiftList? {
        // « Idées de la famille » nobody started yet (récit 9 ter): empty for everyone but the person.
        family.members.firstOrNull { ideasListId(it.id) == listId }?.let { owner ->
            if (owner.id == me.id) return null
            return GiftList(listId, GiftListKind.Ideas, "Idées de la famille", null, null, owner.id, emptyList(), me.id, me.firstName)
        }
        val owner = family.members.firstOrNull { wishesListId(it.id) == listId } ?: return null
        return GiftList(listId, GiftListKind.Wishes, "Mes envies", null, null, owner.id, emptyList(), me.id, me.firstName)
    }

    /** The reader of « Voir comme » is the beneficiary, before the reveal: the rules keep the reservations from them. */
    private fun keptFromReader(list: GiftList): Boolean = readerId != currentMemberId && list.beneficiaryId == readerId && !list.revealed

    private fun state(
        list: GiftList,
        exists: Boolean,
        beneficiary: Member,
        me: Member,
        family: Family,
        items: List<GiftItem>,
        claims: List<GiftClaim>,
        shares: Map<String, List<GiftShare>>,
        day: LocalDate,
        didFail: Failure?,
    ): GiftListUiState.Success {
        val kept = isKeptFrom(list, me) || keptFromReader(list)
        val claimById = claims.associateBy { it.itemId }
        // Read from the live list: the server keeps one reservation per idea, so a refused one
        // means another came first, or the idea was removed meanwhile.
        val lost = (didFail as? Failure.Claim)?.let { failure ->
            when {
                items.none { it.id == failure.itemId } -> ClaimLost(failure.itemId, ClaimLost.Reason.Removed)
                claimById[failure.itemId]?.let { it.holderId != me.id } == true -> ClaimLost(failure.itemId, ClaimLost.Reason.Taken)
                else -> null
            }
        }
        val rows = items.sortedBy { it.title.lowercase() }.map { item ->
            val claim = claimById[item.id]
            val parts = shares[item.id].orEmpty().sortedBy { it.memberName.lowercase() }
            ItemRow(
                item = item,
                canEdit = !list.revealed && item.authorId == me.id && (item.visibleToBeneficiary || claim == null),
                isSurpriseIdea = !item.visibleToBeneficiary && list.kind != GiftListKind.Surprise && list.kind != GiftListKind.Ideas,
                claim = claim?.let {
                    ClaimView(
                        type = it.type,
                        holderName = it.holderName,
                        isMine = it.holderId == me.id,
                        shares = parts,
                        myShare = parts.firstOrNull { share -> share.memberId == me.id },
                        // A reservation made before the server kept the total has none yet:
                        // the part I see is then the best lower bound, rather than 0 €.
                        total = if (it.holderId == me.id) {
                            parts.sumOf { share -> share.amount }
                        } else {
                            maxOf(it.sharesTotal, parts.sumOf { share -> share.amount })
                        },
                        occasion = if (list.kind.isPermanent) it.occasion ?: defaultWishOccasion(beneficiary.birthDate, it.createdOn ?: day) else null,
                    )
                },
            )
        }
        return GiftListUiState.Success(
            list = list,
            beneficiary = beneficiary,
            today = day,
            keptFromMe = isKeptFrom(list, me),
            keptFromReader = keptFromReader(list) && !isKeptFrom(list, me),
            isMine = beneficiary.id == me.id,
            canManage = exists && canManageList(list, me, family),
            canAddItems = !list.revealed && (exists || keepsListsOf(me, beneficiary) || list.kind == GiftListKind.Ideas),
            // Nothing to reserve on a list whose reservations the reader may not see.
            canReserve = exists && canReserve(list, me) && !kept,
            canShare = exists && canShare(list, me) && !kept,
            canSendOutside = exists && canShareWishesOutside(list, me, family),
            canMarkReceived = exists && list.kind == GiftListKind.Wishes && !list.revealed && keepsListsOf(me, beneficiary),
            items = rows,
            myName = me.firstName,
            actionFailed = didFail != null && lost == null,
            claimLost = lost,
        )
    }

    fun retry() {
        attempt.update { it + 1 }
    }

    fun take(itemId: String) = claim(itemId, ClaimType.Solo)

    fun openJointGift(itemId: String) = claim(itemId, ClaimType.Shared)

    private fun claim(itemId: String, type: ClaimType) {
        val me = currentSuccess()?.let { meOf(it) } ?: return
        write(Failure.Claim(itemId)) { it.claim(listId, GiftClaim(itemId, type, me.first, me.second)) }
    }

    fun release(itemId: String) = write { it.release(listId, itemId) }

    /** On « Mes envies », the holder says which occasion the gift is for: it is revealed the day after. */
    fun setClaimOccasion(itemId: String, occasion: GiftOccasion) = write { it.setClaimOccasion(listId, itemId, occasion) }

    /** Joins a joint gift with [amount], or changes one's part. */
    fun setShare(itemId: String, amount: Double) {
        val state = currentSuccess() ?: return
        val me = meOf(state) ?: return
        val isNew = state.items.firstOrNull { it.item.id == itemId }?.claim?.myShare == null
        write { it.setShare(listId, itemId, GiftShare(me.first, me.second, amount, received = false), isNew) }
    }

    fun leaveShare(itemId: String) = write { it.leaveShare(listId, itemId, currentMemberId) }

    fun markReceived(itemId: String, memberId: String, received: Boolean) =
        write { it.markShareReceived(listId, itemId, memberId, received) }

    fun deleteItem(itemId: String) = write { it.deleteItem(listId, itemId) }

    /** « Je l'ai reçue » (rév. 232): the wish goes to « On m'a offert », without a giver. */
    /** The wish waiting for « Oui, reçue »: the gesture cannot be undone, so it is confirmed first. */
    private val _receivedToConfirm = MutableStateFlow<GiftItem?>(null)
    val receivedToConfirm: StateFlow<GiftItem?> = _receivedToConfirm

    fun askWishReceived(item: GiftItem) {
        _receivedToConfirm.value = item
    }

    fun cancelWishReceived() {
        _receivedToConfirm.value = null
    }

    fun confirmWishReceived() {
        val item = _receivedToConfirm.value ?: return
        _receivedToConfirm.value = null
        write { it.markWishReceived(listId, item) }
    }

    fun deleteList(onDone: () -> Unit) {
        val repo = gifts ?: return
        viewModelScope.launch {
            if (send { repo.deleteList(listId) }) onDone() else failed.value = Failure.Write
        }
    }

    private fun currentSuccess(): GiftListUiState.Success? = uiState.value as? GiftListUiState.Success

    /** The signed-in member's id and first name, as the rules expect them in what they write. */
    private fun meOf(state: GiftListUiState.Success): Pair<String, String>? =
        if (state.isMine) null else currentMemberId to state.myName

    /**
     * Sends [block]. Offline, it waits in the queue: after the timeout the screen goes on, but the
     * write is still followed, so that a refusal when the network comes back is said too.
     */
    private fun write(failure: Failure = Failure.Write, block: suspend (GiftRepository) -> Unit) {
        val repo = gifts ?: return
        viewModelScope.launch {
            val sent = async {
                try {
                    block(repo)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            val early = withTimeoutOrNull(writeTimeoutMillis) { sent.await() }
            failed.value = if (early == false) failure else null
            if (early == null && !sent.await()) failed.value = failure
        }
    }

    /** True once the write is accepted, or still pending after the timeout (queued offline). */
    private suspend fun send(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
