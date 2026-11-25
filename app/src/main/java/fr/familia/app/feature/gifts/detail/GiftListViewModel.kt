package fr.familia.app.feature.gifts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import fr.familia.app.core.model.wishProposalId
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftContribution
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.isPermanent
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.caresFor
import fr.familia.app.core.model.MemberProfile
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

/**
 * A wish already bought (récit 68): the total of the parts, mine, and for whoever keeps the lists
 * of the person every first name, never an amount ([parts], empty for the others, récit 93).
 */
data class ContributionView(
    val total: Int,
    val mine: Int? = null,
    val parts: List<GiftContribution> = emptyList(),
)

data class ItemRow(
    val item: GiftItem,
    /** Edit and delete: the author, until it is reserved; the beneficiary's side always. */
    val canEdit: Boolean,
    /** A surprise idea of someone else: shown to the viewers with « Idée de … ». */
    val isSurpriseIdea: Boolean,
    /** Null when free, or when the reservations are hidden from this member. */
    val claim: ClaimView?,
    /** « Déjà acheté, participation bienvenue » (récit 68): never reserved, only taken part in. */
    val contributions: ContributionView? = null,
    /** « Cadeau commun avec… » (récit 73): the first name of the member this wish is shared with. */
    val sharedWithName: String? = null,
    /**
     * The reader shares this wish (récit 73): like its beneficiary, they see neither whether it is
     * reserved nor by whom, and have nothing to reserve or take part in.
     */
    val sharedWithMe: Boolean = false,
)

/** A wish of someone of the household shared with the person of the list (récit 73), shown at its foot. */
data class SharedWishRow(val listId: String, val item: GiftItem, val ownerName: String)

/** Why a reservation did not hold, told gently rather than as a failure. */
data class ClaimLost(val itemId: String, val reason: Reason) {
    enum class Reason { Taken, Removed, AlreadyBought }
}

/**
 * Where a wish stands in the surprises for the person (brief §5, fiche C8), read by the members of
 * one: free, someone takes it, or a joint gift with what is gathered.
 */
sealed interface WishSurpriseState {
    data object Free : WishSurpriseState
    data class Taken(val by: String, val mine: Boolean) : WishSurpriseState
    data class Joint(val by: String, val gathered: Int, val goal: Int) : WishSurpriseState
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
        /** « Je participe » on a wish already bought (récit 68): an adult who does not keep the person's lists. */
        val canContribute: Boolean = false,
        /** Who takes part, with their amounts: only whoever keeps the person's lists, never in « Voir comme ». */
        val seesContributors: Boolean = false,
        /** « Cadeaux communs » (récit 73): the wishes of the household shared with the person, opened in their list. */
        val sharedWishes: List<SharedWishRow> = emptyList(),
        /**
         * La bascule (plan Cadeaux et Surprise, PR 3): a wish of someone else is read only, given
         * through a surprise, « Offrir cette envie ». Never on my own wishes nor in « Voir comme ».
         */
        val canOffer: Boolean = false,
        /** The surprise I am in for the person, the one « Offrir cette envie » opens; null to start one. */
        val surpriseId: String? = null,
        /** Each wish in the surprises I am in for the person, by item. */
        val wishStates: Map<String, WishSurpriseState> = emptyMap(),
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
    /** The switch « Déjà acheté » (récit 68), read with isOnForFamily. */
    private val alreadyBought: Boolean = false,
    /** The surprises, for « Offrir cette envie » and the state of each wish in them. */
    private val surprises: SurpriseRepository? = null,
    /**
     * La bascule (plan Cadeaux et Surprise, PR 3): a gift is reserved and shared only in a surprise;
     * the rules refuse the old reservations, their parts and « Je participe ». The app sets it.
     */
    private val surprisesOnly: Boolean = false,
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
        // Récit 73: whoever shares a wish of the list reads the reservations of the others one by one.
        val sharing = setOf(me.id, readerId).filter { it in list.coBeneficiaryIds }.toSet()
        val claims = when {
            stored == null || kept -> flowOf(emptyList<GiftClaim>())
            sharing.isNotEmpty() -> items
                .map { all -> all.filter { it.coBeneficiaryId !in sharing }.map { it.id }.sorted() }
                .distinctUntilChanged()
                .flatMapLatest { ids -> repo.observeClaimsOf(list.id, ids) }
            else -> repo.observeClaims(list.id)
        }
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
        val parts = contributions(repo, list, beneficiary, me, items)
        val reservations = combine(claims, shares, parts) { c, s, p -> Triple(c, s, p) }
        val shared = combine(sharedWishes(repo, list, beneficiary, family), surpriseTakes(list, beneficiary, me, kept)) { a, b -> a to b }
        return combine(items, reservations, today, failed, shared) { itemList, (claimList, shareMap, partMap), day, didFail, (sharedList, offer) ->
            val base = state(list, stored != null, beneficiary, me, family, itemList, claimList, shareMap, partMap, day, didFail)
            val names = family.members.associate { it.id to it.firstName }
            base.copy(
                sharedWishes = sharedList,
                canOffer = offer != null,
                surpriseId = offer?.first,
                wishStates = if (offer == null) {
                    emptyMap()
                } else {
                    itemList.associate { item -> item.id to wishState(offer.second[wishProposalId(item.id)], names, me.id) }
                },
            )
        }
    }

    /**
     * The first surprise I am in for the person, and what is taken in all of them, by proposal; null
     * where « Offrir cette envie » has no place (my own wishes, another list, « Voir comme »).
     */
    private fun surpriseTakes(list: GiftList, beneficiary: Member, me: Member, kept: Boolean): Flow<Pair<String?, Map<String, SurpriseTake>>?> {
        val repo = surprises
        if (repo == null || list.kind != GiftListKind.Wishes || beneficiary.id == me.id || kept || readerId != currentMemberId) return flowOf(null)
        return repo.observeMine(me.id)
            .map { all -> all.filter { it.beneficiaryId == beneficiary.id }.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(null to emptyMap())
                } else {
                    combine(ids.map { id -> repo.observeTakes(id).catch { emit(emptyList()) } }) { all ->
                        ids.first() to all.toList().flatten().associateBy { it.proposalId }
                    }
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(null to emptyMap())
            }
    }

    private fun wishState(take: SurpriseTake?, names: Map<String, String>, meId: String): WishSurpriseState = when {
        take == null -> WishSurpriseState.Free
        take.kind == TakeKind.Shared -> WishSurpriseState.Joint(names[take.ownerId].orEmpty(), take.sharesTotal, take.goal ?: 0)
        else -> WishSurpriseState.Taken(names[take.ownerId].orEmpty(), take.ownerId == meId)
    }

    /**
     * The parts of the wishes already bought (récit 68): every one for whoever keeps the person's
     * lists, my own for the others. None in « Voir comme »: the rules answer for the admin principal.
     */
    private fun contributions(
        repo: GiftRepository,
        list: GiftList,
        beneficiary: Member,
        me: Member,
        items: Flow<List<GiftItem>>,
    ): Flow<Map<String, List<GiftContribution>>> {
        if (!alreadyBought || list.kind != GiftListKind.Wishes || readerId != currentMemberId) return flowOf(emptyMap())
        val all = keepsListsOf(me, beneficiary)
        return items.map { rows -> rows.filter { it.alreadyBought && it.visibleToBeneficiary }.map { it.id }.sorted() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                // A part never takes the whole list down: the wish then shows its total only.
                repo.observeContributions(list.id, ids, me.id, all).catch { e ->
                    if (e is CancellationException) throw e
                    emit(emptyMap())
                }
            }
            .onStart { emit(emptyMap()) }
    }

    /**
     * « Cadeaux communs » (récit 73): on « Mes envies » of [beneficiary], the wishes the other members
     * of their household share with them. They never take the list down.
     */
    private fun sharedWishes(repo: GiftRepository, list: GiftList, beneficiary: Member, family: Family): Flow<List<SharedWishRow>> {
        if (list.kind != GiftListKind.Wishes) return flowOf(emptyList())
        val household = family.members.filter { it.householdId == beneficiary.householdId && it.id != beneficiary.id }
        if (household.isEmpty()) return flowOf(emptyList())
        val names = household.associate { wishesListId(it.id) to it.firstName }
        return repo.observeWishesSharedWith(beneficiary.id, names.keys.sorted())
            .map { wishes ->
                wishes.map { SharedWishRow(it.listId, it.item, names[it.listId].orEmpty()) }
                    .sortedBy { it.item.title.lowercase() }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyList())
            }
            .onStart { emit(emptyList()) }
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
        contributionsById: Map<String, List<GiftContribution>>,
        day: LocalDate,
        didFail: Failure?,
    ): GiftListUiState.Success {
        val seesContributors = keepsListsOf(me, beneficiary) && readerId == currentMemberId
        val kept = isKeptFrom(list, me) || keptFromReader(list)
        val claimById = claims.associateBy { it.itemId }
        // Read from the live list: the server keeps one reservation per idea, so a refused one
        // means another came first, or the idea was removed meanwhile.
        val lost = (didFail as? Failure.Claim)?.let { failure ->
            when {
                items.none { it.id == failure.itemId } -> ClaimLost(failure.itemId, ClaimLost.Reason.Removed)
                claimById[failure.itemId]?.let { it.holderId != me.id } == true -> ClaimLost(failure.itemId, ClaimLost.Reason.Taken)
                // Checked « Déjà acheté » while its switch was on, then the switch went off: the server still
                // refuses « Je le prends » on it, so the screen says why (notes C de #370).
                items.firstOrNull { it.id == failure.itemId }?.alreadyBought == true ->
                    ClaimLost(failure.itemId, ClaimLost.Reason.AlreadyBought)
                else -> null
            }
        }
        val sharing = setOf(me.id, readerId)
        val rows = items.sortedBy { it.title.lowercase() }.map { item ->
            val sharedWithMe = item.coBeneficiaryId != null && item.coBeneficiaryId in sharing
            val claim = claimById[item.id]?.takeIf { !sharedWithMe }
            val parts = shares[item.id].orEmpty().sortedBy { it.memberName.lowercase() }
            ItemRow(
                item = item,
                canEdit = !list.revealed && (
                    (item.authorId == me.id && (item.visibleToBeneficiary || claim == null)) ||
                        // Récit 73: whoever shares the wish, or keeps the lists of a child who does.
                        (list.kind == GiftListKind.Wishes && item.coBeneficiaryId?.let { co ->
                            family.members.firstOrNull { it.id == co }?.let { keepsListsOf(me, it) }
                        } == true) ||
                        // Récit 114: the adults who look after a child edit its wishes, never a surprise idea.
                        (list.kind == GiftListKind.Wishes && item.visibleToBeneficiary && caresFor(me, beneficiary))
                    ),
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
                contributions = if (alreadyBought && list.kind == GiftListKind.Wishes && item.alreadyBought && item.visibleToBeneficiary) {
                    val itemParts = contributionsById[item.id].orEmpty()
                    ContributionView(
                        total = item.contributedTotal,
                        mine = itemParts.firstOrNull { it.memberId == me.id }?.amount,
                        parts = if (seesContributors) itemParts.sortedBy { it.memberName.lowercase() } else emptyList(),
                    )
                } else {
                    null
                },
                sharedWithName = item.coBeneficiaryId?.let { co -> family.members.firstOrNull { it.id == co }?.firstName },
                sharedWithMe = sharedWithMe,
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
            canReserve = !surprisesOnly && exists && canReserve(list, me) && !kept,
            canShare = !surprisesOnly && exists && canShare(list, me) && !kept,
            canSendOutside = exists && canShareWishesOutside(list, me, family),
            canMarkReceived = exists && list.kind == GiftListKind.Wishes && !list.revealed && keepsListsOf(me, beneficiary),
            canContribute = !surprisesOnly && exists && alreadyBought && list.kind == GiftListKind.Wishes && !kept &&
                me.profile == MemberProfile.Adult && !keepsListsOf(me, beneficiary),
            seesContributors = seesContributors,
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

    /** The page of one of my wishes (correctif E9): its occasion changed in one touch. */
    fun setWishOccasion(itemId: String, occasion: GiftOccasion?) {
        val item = currentSuccess()?.items?.firstOrNull { it.item.id == itemId }?.item ?: return
        if (item.occasion == occasion) return
        write { it.saveItem(listId, item.copy(occasion = occasion), isNew = false) }
    }

    /** « Retirer de mes envies » (correctif E10), once confirmed. */
    fun removeWish(itemId: String) = write { it.deleteItem(listId, itemId) }

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

    /** « Je participe » with [amount] whole euros, or a new amount (récit 68). */
    fun contribute(itemId: String, amount: Int) {
        val state = currentSuccess()?.takeIf { it.canContribute } ?: return
        val row = state.items.firstOrNull { it.item.id == itemId }?.contributions ?: return
        if (amount !in 1..CONTRIBUTION_MAX) return
        val previous = row.mine ?: 0
        write { it.setContribution(listId, itemId, GiftContribution(currentMemberId, state.myName, amount), previous) }
    }

    fun withdrawContribution(itemId: String) {
        val mine = currentSuccess()?.items?.firstOrNull { it.item.id == itemId }?.contributions?.mine ?: return
        write { it.removeContribution(listId, itemId, currentMemberId, mine) }
    }

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
        // Only the last write tells: a late refusal of an older one never covers a newer one.
        val ticket = ++writes
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
            if (ticket == writes) failed.value = if (early == false) failure else null
            if (early == null && !sent.await() && ticket == writes) failed.value = failure
        }
    }

    /** How many writes this screen sent: the number of the last one. */
    private var writes = 0

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

/** The largest part the rules accept, in whole euros (récit 68). */
const val CONTRIBUTION_MAX = 10_000
