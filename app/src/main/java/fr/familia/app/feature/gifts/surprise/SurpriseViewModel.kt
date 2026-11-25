package fr.familia.app.feature.gifts.surprise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import fr.familia.app.core.model.jointGoal
import fr.familia.app.core.model.ideaProposalId
import fr.familia.app.core.model.wishProposalId
import fr.familia.app.core.model.wishesListId
import kotlinx.coroutines.CancellationException
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.isToConfirm
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A proposal as I see it: its average (from 3 notes) and my own note. */
data class ProposalRow(val proposal: SurpriseProposal, val myStars: Int?, val isMine: Boolean = false)

/**
 * One line of « Résultats » (fiche C5): its rank by average, what is taken and by whom, my part of a
 * joint gift, and the parts of the others when the joint gift is mine (nobody else reads them).
 */
data class ResultRow(
    val proposal: SurpriseProposal,
    val rank: Int,
    val take: SurpriseTake? = null,
    val myShare: Int? = null,
    val ownerName: String? = null,
    val takenByMe: Boolean = false,
    /** Mine to read: first name (or « Toi ») and amount, the owner of the joint gift only. */
    val shares: List<Pair<String, Int>> = emptyList(),
    val best: Boolean = false,
    val unanimous: Boolean = false,
    val isMine: Boolean = false,
)

/** Where a member of the surprise stands (fiche C6). */
enum class MemberState { Waiting, Done, Declined }

data class MemberStatus(val memberId: String, val name: String, val isMe: Boolean, val state: MemberState)

/** One idea of my notebook for the person: 🔒 Privée until I add it, then ✓ Ajoutée. */
data class MyIdeaRow(val entry: NotebookEntry, val added: Boolean)

sealed interface SurpriseUiState {
    data object Loading : SurpriseUiState

    /** Gone, or I am not in it (the beneficiary never gets here: the rules refuse the read). */
    data object Unavailable : SurpriseUiState

    data class Ready(
        val surprise: Surprise,
        val beneficiary: Member,
        val meId: String,
        val wishes: List<ProposalRow>,
        val myIdeas: List<MyIdeaRow>,
        val othersIdeas: List<ProposalRow>,
        val history: List<GiftHistoryItem>,
        val failed: Boolean = false,
        val results: List<ResultRow> = emptyList(),
        val removed: List<ResultRow> = emptyList(),
        val statuses: List<MemberStatus> = emptyList(),
        /** A child takes alone and never puts a part (PRODUIT-19, the rules). */
        val isAdult: Boolean = true,
        val validated: Boolean = false,
        val declined: Boolean = false,
        /** My reminders of this surprise on mute (⚙). */
        val muted: Boolean = false,
        /** The day itself or after: the big orange card « C'est offert » (fiche C7). */
        val isDay: Boolean = false,
    ) : SurpriseUiState {
        val isCreator: Boolean get() = surprise.creatorId == meId

        /** What the tab « Propositions » counts: the wishes and the ideas added. */
        val proposalCount: Int get() = wishes.size + othersIdeas.size + myIdeas.count { it.added }

        /** What the tab « Résultats » counts: the gifts taken. */
        val takenCount: Int get() = results.count { it.take != null }

        /** « Valider » asks that I took a gift or put a part (brief §6). */
        val participates: Boolean get() = results.any { it.takenByMe || it.myShare != null }

        /** « Prête »: everyone who takes part validated; nothing is blocked (brief §6). */
        val isReady: Boolean get() = statuses.any { it.state == MemberState.Done } && statuses.none { it.state == MemberState.Waiting }
    }
}

/** Everything a surprise is built from, read together. */
private data class Sources(
    val surprise: Surprise?,
    val members: List<Member>,
    val wishItems: List<GiftItem>,
    val proposals: List<SurpriseProposal>,
    val myRatings: Map<String, Int>,
)

/** What is taken in it, and who stands where. */
private data class Gifts(
    val takes: List<SurpriseTake> = emptyList(),
    val myShares: Map<String, Int> = emptyMap(),
    val ownShares: Map<String, Map<String, Int>> = emptyMap(),
    val rows: List<SurpriseMemberRow> = emptyList(),
    val today: LocalDate? = null,
)

/**
 * The surprise itself, tab « Propositions » (brief §4, fiche C4): the wishes of the person, all in
 * it, each noted from 1 to 5; « Mes idées », private until added; the ideas of the others; « Déjà
 * offert à X ». The average shows from 3 notes, and my note is the only one I read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurpriseViewModel(
    private val surprises: SurpriseRepository?,
    members: MemberRepository?,
    gifts: GiftRepository?,
    private val notebook: NotebookRepository?,
    private val currentMemberId: String,
    private val surpriseId: String,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    val state: StateFlow<SurpriseUiState> = run {
        val repo = surprises ?: return@run flowOf(SurpriseUiState.Unavailable)
        val memberRepo = members ?: return@run flowOf(SurpriseUiState.Unavailable)
        repo.observeSurprise(surpriseId).flatMapLatest { surprise ->
            if (surprise == null || currentMemberId !in surprise.memberIds) return@flatMapLatest flowOf(SurpriseUiState.Unavailable)
            val wishItems: Flow<List<GiftItem>> = gifts?.observeItems(wishesListId(surprise.beneficiaryId), hiddenFromMe = false)
                ?.catch { emit(emptyList()) } ?: flowOf(emptyList())
            val entries: Flow<List<NotebookEntry>> = notebook?.observeEntries(currentMemberId)?.catch { emit(emptyList()) } ?: flowOf(emptyList())
            val history = repo.observeHistory(surprise.beneficiaryId).catch { emit(emptyList()) }
            val takes = repo.observeTakes(surpriseId).catch { emit(emptyList()) }
            // The parts of my own joint gifts: one listener each, read by me as their owner.
            val ownShares: Flow<Map<String, Map<String, Int>>> = takes.flatMapLatest { list ->
                val mine = list.filter { it.ownerId == currentMemberId && it.kind == TakeKind.Shared }.map { it.proposalId }
                if (mine.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(mine.map { pid -> repo.observeShares(surpriseId, pid).catch { emit(emptyMap()) }.map { pid to it } }) { it.toMap() }
                }
            }
            val gifts = combine(
                combine(takes, today) { t, day -> t to day },
                repo.observeMyShares(surpriseId, currentMemberId).catch { emit(emptyMap()) },
                ownShares,
                repo.observeMemberRows(surpriseId).catch { emit(emptyList()) },
            ) { (t, day), mine, own, rows -> Gifts(t, mine, own, rows, day) }
            combine(
                combine(memberRepo.observeFamily(), wishItems, repo.observeProposals(surpriseId), repo.observeMyRatings(surpriseId, currentMemberId)) { family, items, proposals, ratings ->
                    Sources(surprise, family.members, items, proposals, ratings)
                },
                entries,
                history,
                gifts,
                failed,
            ) { sources, notes, given, g, fail ->
                surpriseState(sources, notes, given, currentMemberId, g)?.copy(failed = fail) ?: SurpriseUiState.Unavailable
            }
        }
    }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(SurpriseUiState.Unavailable)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SurpriseUiState.Loading)

    /** Notes a proposal; the same star again takes my note back. */
    fun rate(row: ProposalRow, stars: Int) {
        val repo = surprises ?: return
        val next = if (row.myStars == stars) null else stars
        write { repo.rate(surpriseId, row.proposal, currentMemberId, next) }
    }

    /** 🔒 Privée ↔ ✓ Ajoutée for one of my ideas. */
    fun toggleIdea(row: MyIdeaRow, myName: String) {
        val repo = surprises ?: return
        val id = ideaProposalId(row.entry.id)
        write {
            if (row.added) {
                repo.removeIdea(surpriseId, id)
            } else {
                repo.addIdea(
                    surpriseId,
                    SurpriseProposal(id, ProposalKind.Idea, row.entry.title, row.entry.price, row.entry.url, currentMemberId, myName),
                )
            }
        }
    }

    /** ✕ of Résultats, or « Remettre » for one taken out. */
    fun setRemoved(row: ResultRow, removed: Boolean) {
        val repo = surprises ?: return
        write { repo.setRemoved(surpriseId, row.proposal, removed) }
    }

    /** « 🎁 Je le prends ». */
    fun takeAlone(row: ResultRow) {
        val repo = surprises ?: return
        write { repo.take(surpriseId, row.proposal, currentMemberId, TakeKind.Solo, null) }
    }

    /** « 👪 Cadeau commun »: its goal is the price, in whole euros. */
    fun takeTogether(row: ResultRow) {
        val repo = surprises ?: return
        write { repo.take(surpriseId, row.proposal, currentMemberId, TakeKind.Shared, jointGoal(row.proposal.price)) }
    }

    /** « Je ne le prends plus », or the joint gift closed by its owner. */
    fun release(row: ResultRow) {
        val repo = surprises ?: return
        write { repo.release(surpriseId, row.proposal.id) }
    }

    /** « Ma part » in whole euros; 0 takes it back. */
    fun setShare(row: ResultRow, amount: Int) {
        val repo = surprises ?: return
        val next = amount.takeIf { it > 0 }?.coerceAtMost(SurpriseTake.MAX_AMOUNT)
        if (next == row.myShare) return
        write { repo.setShare(surpriseId, row.proposal.id, currentMemberId, next) }
    }

    /** « Valider ma participation », or take it back. */
    fun setValidated(validated: Boolean) {
        val repo = surprises ?: return
        write { repo.setValidated(surpriseId, currentMemberId, validated) }
    }

    /** The reminders of this surprise on mute, for me alone. */
    fun setMuted(muted: Boolean) {
        val repo = surprises ?: return
        write { repo.setMuted(surpriseId, currentMemberId, muted) }
    }

    /** « Réinviter » a member who said « Je ne participe pas »: the creator alone (brief §4). */
    fun reinvite(memberId: String) {
        val repo = surprises ?: return
        write { repo.reinvite(surpriseId, memberId) }
    }

    /** « Je ne participe pas »: final. What I took is released first, so the others can take it. */
    fun decline(state: SurpriseUiState.Ready) {
        val repo = surprises ?: return
        write {
            state.results.filter { it.myShare != null }.forEach { repo.setShare(surpriseId, it.proposal.id, currentMemberId, null) }
            state.results.filter { it.takenByMe }.forEach { repo.release(surpriseId, it.proposal.id) }
            repo.decline(surpriseId, currentMemberId)
        }
    }

    /** « C'est offert »: confirmed for everyone; the surprise then leaves every screen. */
    fun markOffered() {
        val repo = surprises ?: return
        write { repo.markOffered(surpriseId, currentMemberId) }
    }

    private fun write(block: suspend () -> Unit) {
        failed.value = false
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            }
        }
    }
}

/** The state from what is read. Pure, for the tests; null when the person left the family. */
private fun surpriseState(
    sources: Sources,
    entries: List<NotebookEntry>,
    history: List<GiftHistoryItem>,
    meId: String,
    gifts: Gifts = Gifts(),
): SurpriseUiState.Ready? {
    val surprise = sources.surprise ?: return null
    val beneficiary = sources.members.firstOrNull { it.id == surprise.beneficiaryId } ?: return null
    val byId = sources.proposals.associateBy { it.id }
    // Every wish is in the surprise (brief §4), noted or not yet; one the person removed leaves it.
    val wishes = sources.wishItems.map { item ->
        val id = wishProposalId(item.id)
        val stored = byId[id]
        val proposal = SurpriseProposal(
            id = id,
            kind = ProposalKind.Wish,
            title = item.title,
            price = item.price,
            url = item.url,
            authorId = beneficiary.id,
            authorName = beneficiary.firstName,
            removed = stored?.removed == true,
            ratingSum = stored?.ratingSum ?: 0,
            ratingCount = stored?.ratingCount ?: 0,
        )
        ProposalRow(proposal, sources.myRatings[id])
    }
    val ideas = sources.proposals.filter { it.kind == ProposalKind.Idea }
    val myIdeas = entries
        .filter { it.source == NotebookSource.Idea && it.personId == beneficiary.id && it.movedTo == null && it.status != GiftStatus.Given }
        .map { entry -> MyIdeaRow(entry, added = byId[ideaProposalId(entry.id)]?.authorId == meId) }
    val others = ideas.filter { it.authorId != meId }.map { ProposalRow(it, sources.myRatings[it.id]) }
    val mineAdded = ideas.filter { it.authorId == meId }.map { ProposalRow(it, sources.myRatings[it.id], isMine = true) }
    val names = sources.members.associate { it.id to it.firstName }
    val declined = gifts.rows.filter { it.declined }.map { it.memberId }.toSet()
    val active = surprise.memberIds.filter { it !in declined }
    val takes = gifts.takes.associateBy { it.proposalId }
    // The wishes as they are now, then every idea added (mine too), each once.
    val all = wishes.map { it.proposal } + ideas
    fun row(p: SurpriseProposal, rank: Int): ResultRow {
        val take = takes[p.id]
        val own = gifts.ownShares[p.id].orEmpty()
        return ResultRow(
            proposal = p,
            rank = rank,
            take = take,
            myShare = gifts.myShares[p.id],
            ownerName = take?.let { names[it.ownerId] },
            takenByMe = take?.ownerId == meId,
            shares = own.entries.sortedByDescending { it.value }.map { (id, amount) -> (if (id == meId) "" else names[id].orEmpty()) to amount },
            isMine = p.kind == ProposalKind.Idea && p.authorId == meId,
            unanimous = p.ratingCount >= SurpriseProposal.MIN_RATINGS && p.ratingCount >= active.size && p.ratingSum == 5 * p.ratingCount,
        )
    }
    val ranked = all.filter { !it.removed }
        .sortedWith(compareByDescending<SurpriseProposal> { it.average ?: -1.0 }.thenByDescending { it.ratingCount }.thenBy { it.title.lowercase() })
        .mapIndexed { i, p -> row(p, i + 1) }
        .mapIndexed { i, r -> if (i == 0 && r.proposal.average != null) r.copy(best = true) else r }
    val myRow = gifts.rows.firstOrNull { it.memberId == meId }
    val participates = ranked.any { it.takenByMe || it.myShare != null }
    // « Valider » goes away by itself when I take part in nothing any more (brief §6).
    val validated = myRow?.validated == true && participates
    val statuses = surprise.memberIds.map { id ->
        val line = gifts.rows.firstOrNull { it.memberId == id }
        val state = when {
            line?.declined == true -> MemberState.Declined
            id == meId -> if (validated) MemberState.Done else MemberState.Waiting
            line?.validated == true -> MemberState.Done
            else -> MemberState.Waiting
        }
        MemberStatus(id, names[id].orEmpty(), id == meId, state)
    }.sortedBy { if (it.isMe) 0 else 1 }
    return SurpriseUiState.Ready(
        surprise = surprise,
        beneficiary = beneficiary,
        meId = meId,
        wishes = wishes,
        myIdeas = myIdeas,
        othersIdeas = others + mineAdded.filter { row -> myIdeas.none { ideaProposalId(it.entry.id) == row.proposal.id } },
        history = history,
        results = ranked,
        removed = all.filter { it.removed }.map { row(it, 0) },
        statuses = statuses,
        isAdult = sources.members.firstOrNull { it.id == meId }?.profile == MemberProfile.Adult,
        validated = validated,
        declined = myRow?.declined == true,
        muted = myRow?.muted == true,
        isDay = gifts.today?.let { surprise.isToConfirm(it) } == true,
    )
}

/** For the tests. */
internal fun surpriseStateForTest(
    surprise: Surprise,
    members: List<Member>,
    wishItems: List<GiftItem>,
    proposals: List<SurpriseProposal>,
    myRatings: Map<String, Int>,
    entries: List<NotebookEntry>,
    history: List<GiftHistoryItem>,
    meId: String,
    takes: List<SurpriseTake> = emptyList(),
    myShares: Map<String, Int> = emptyMap(),
    ownShares: Map<String, Map<String, Int>> = emptyMap(),
    rows: List<SurpriseMemberRow> = emptyList(),
    today: LocalDate? = null,
): SurpriseUiState.Ready? =
    surpriseState(Sources(surprise, members, wishItems, proposals, myRatings), entries, history, meId, Gifts(takes, myShares, ownShares, rows, today))
