package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.OfferedSurprise
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.sortOffered
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In memory, with the read rule of the server: only a member reads a surprise. */
class FakeSurprises(initial: List<Surprise> = emptyList(), private val failWith: Exception? = null) : SurpriseRepository {
    val surprises = MutableStateFlow(initial)
    private var next = 0

    override fun observeMine(memberId: String): Flow<List<Surprise>> =
        surprises.map { list -> list.filter { memberId in it.memberIds }.sortedBy { it.date } }

    override fun observeSurprise(id: String): Flow<Surprise?> = surprises.map { list -> list.firstOrNull { it.id == id } }

    override fun observeOpenFor(beneficiaryId: String): Flow<List<OpenSurprise>> = surprises.map { list ->
        list.filter { it.beneficiaryId == beneficiaryId }.map { OpenSurprise(it.id, it.beneficiaryId, it.title, it.creatorName) }
    }

    override fun newSurpriseId(): String = "s-new-${next++}"

    override suspend fun saveSurprise(surprise: Surprise, isNew: Boolean) {
        failWith?.let { throw it }
        surprises.value = surprises.value.filter { it.id != surprise.id } + surprise
    }

    override suspend fun deleteSurprise(id: String) {
        failWith?.let { throw it }
        surprises.value = surprises.value.filter { it.id != id }
    }

    override suspend fun joinSurprise(id: String, memberId: String) {
        failWith?.let { throw it }
        surprises.value = surprises.value.map { if (it.id == id && memberId !in it.memberIds) it.copy(memberIds = it.memberIds + memberId) else it }
    }

    val proposals = MutableStateFlow<Map<String, List<SurpriseProposal>>>(emptyMap())
    /** surpriseId/memberId → proposal → stars. */
    val ratings = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val history = MutableStateFlow<Map<String, List<GiftHistoryItem>>>(emptyMap())

    override fun observeProposals(surpriseId: String): Flow<List<SurpriseProposal>> = proposals.map { it[surpriseId].orEmpty() }

    override fun observeMyRatings(surpriseId: String, memberId: String): Flow<Map<String, Int>> =
        ratings.map { it["$surpriseId/$memberId"].orEmpty() }

    override suspend fun rate(surpriseId: String, proposal: SurpriseProposal, memberId: String, stars: Int?) {
        failWith?.let { throw it }
        val key = "$surpriseId/$memberId"
        val mine = ratings.value[key].orEmpty()
        ratings.value = ratings.value + (key to if (stars == null) mine - proposal.id else mine + (proposal.id to stars))
        val list = proposals.value[surpriseId].orEmpty()
        if (list.none { it.id == proposal.id }) proposals.value = proposals.value + (surpriseId to list + proposal.copy(ratingSum = 0, ratingCount = 0))
    }

    override suspend fun addIdea(surpriseId: String, proposal: SurpriseProposal) {
        failWith?.let { throw it }
        proposals.value = proposals.value + (surpriseId to proposals.value[surpriseId].orEmpty().filter { it.id != proposal.id } + proposal)
    }

    override suspend fun removeIdea(surpriseId: String, proposalId: String) {
        failWith?.let { throw it }
        proposals.value = proposals.value + (surpriseId to proposals.value[surpriseId].orEmpty().filter { it.id != proposalId })
    }

    override fun observeHistory(beneficiaryId: String): Flow<List<GiftHistoryItem>> = history.map { it[beneficiaryId].orEmpty() }

    override suspend fun setRemoved(surpriseId: String, proposal: SurpriseProposal, removed: Boolean) {
        failWith?.let { throw it }
        val list = proposals.value[surpriseId].orEmpty()
        val stored = list.firstOrNull { it.id == proposal.id } ?: proposal
        proposals.value = proposals.value + (surpriseId to list.filter { it.id != proposal.id } + stored.copy(removed = removed))
    }

    val takes = MutableStateFlow<Map<String, List<SurpriseTake>>>(emptyMap())
    /** surpriseId/proposalId → member → amount. */
    val shares = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val rows = MutableStateFlow<Map<String, List<SurpriseMemberRow>>>(emptyMap())

    override fun observeTakes(surpriseId: String): Flow<List<SurpriseTake>> = takes.map { it[surpriseId].orEmpty() }

    override suspend fun take(surpriseId: String, proposal: SurpriseProposal, memberId: String, kind: TakeKind, goal: Int?) {
        failWith?.let { throw it }
        val list = proposals.value[surpriseId].orEmpty()
        if (list.none { it.id == proposal.id }) proposals.value = proposals.value + (surpriseId to list + proposal)
        takes.value = takes.value + (surpriseId to takes.value[surpriseId].orEmpty() + SurpriseTake(proposal.id, kind, memberId, goal = goal))
    }

    override suspend fun release(surpriseId: String, proposalId: String) {
        failWith?.let { throw it }
        takes.value = takes.value + (surpriseId to takes.value[surpriseId].orEmpty().filter { it.proposalId != proposalId })
        shares.value = shares.value - "$surpriseId/$proposalId"
    }

    override fun observeMyShares(surpriseId: String, memberId: String): Flow<Map<String, Int>> = shares.map { all ->
        all.filterKeys { it.startsWith("$surpriseId/") }
            .mapNotNull { (key, byMember) -> byMember[memberId]?.let { key.removePrefix("$surpriseId/") to it } }.toMap()
    }

    override fun observeShares(surpriseId: String, proposalId: String): Flow<Map<String, Int>> = shares.map { it["$surpriseId/$proposalId"].orEmpty() }

    override suspend fun setShare(surpriseId: String, proposalId: String, memberId: String, amount: Int?) {
        failWith?.let { throw it }
        val key = "$surpriseId/$proposalId"
        val now = shares.value[key].orEmpty()
        val next = if (amount == null) now - memberId else now + (memberId to amount)
        shares.value = shares.value + (key to next)
        // The server keeps the total on the take.
        takes.value = takes.value + (surpriseId to takes.value[surpriseId].orEmpty().map {
            if (it.proposalId == proposalId) it.copy(sharesTotal = next.values.sum(), sharesCount = next.size) else it
        })
    }

    override fun observeMemberRows(surpriseId: String): Flow<List<SurpriseMemberRow>> = rows.map { it[surpriseId].orEmpty() }

    override suspend fun setValidated(surpriseId: String, memberId: String, validated: Boolean) {
        failWith?.let { throw it }
        updateRow(surpriseId, memberId) { it.copy(validated = validated) }
    }

    override suspend fun decline(surpriseId: String, memberId: String) {
        failWith?.let { throw it }
        updateRow(surpriseId, memberId) { it.copy(declined = true, validated = false) }
    }

    val offered = mutableListOf<Pair<String, String>>()

    override suspend fun markOffered(surpriseId: String, memberId: String) {
        failWith?.let { throw it }
        offered += surpriseId to memberId
        // Offered, it is gone for everyone: no access to a past surprise (DESIGN-162).
        surprises.value = surprises.value.filter { it.id != surpriseId }
    }

    val offeredList = MutableStateFlow<List<OfferedSurprise>>(emptyList())
    val cancelAsked = mutableListOf<String>()

    override fun observeOffered(adminId: String): Flow<List<OfferedSurprise>> = offeredList.map { sortOffered(it) }

    override suspend fun askCancelOffered(surpriseId: String) {
        failWith?.let { throw it }
        cancelAsked += surpriseId
        offeredList.value = offeredList.value.map { if (it.id == surpriseId) it.copy(cancelAsked = true) else it }
    }

    private fun updateRow(surpriseId: String, memberId: String, change: (SurpriseMemberRow) -> SurpriseMemberRow) {
        val list = rows.value[surpriseId].orEmpty()
        val row = change(list.firstOrNull { it.memberId == memberId } ?: SurpriseMemberRow(memberId))
        rows.value = rows.value + (surpriseId to list.filter { it.memberId != memberId } + row)
    }
}
