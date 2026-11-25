package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.Surprise
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
}
