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
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.ideaProposalId
import fr.familia.app.core.model.wishProposalId
import fr.familia.app.core.model.wishesListId
import kotlinx.coroutines.CancellationException
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
    ) : SurpriseUiState {
        val isCreator: Boolean get() = surprise.creatorId == meId

        /** What the tab « Propositions » counts: the wishes and the ideas added. */
        val proposalCount: Int get() = wishes.size + othersIdeas.size + myIdeas.count { it.added }
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
            combine(
                combine(memberRepo.observeFamily(), wishItems, repo.observeProposals(surpriseId), repo.observeMyRatings(surpriseId, currentMemberId)) { family, items, proposals, ratings ->
                    Sources(surprise, family.members, items, proposals, ratings)
                },
                entries,
                history,
                failed,
            ) { sources, notes, given, fail -> surpriseState(sources, notes, given, currentMemberId)?.copy(failed = fail) ?: SurpriseUiState.Unavailable }
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
private fun surpriseState(sources: Sources, entries: List<NotebookEntry>, history: List<GiftHistoryItem>, meId: String): SurpriseUiState.Ready? {
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
    return SurpriseUiState.Ready(
        surprise = surprise,
        beneficiary = beneficiary,
        meId = meId,
        wishes = wishes,
        myIdeas = myIdeas,
        othersIdeas = others + mineAdded.filter { row -> myIdeas.none { ideaProposalId(it.entry.id) == row.proposal.id } },
        history = history,
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
): SurpriseUiState.Ready? = surpriseState(Sources(surprise, members, wishItems, proposals, myRatings), entries, history, meId)
