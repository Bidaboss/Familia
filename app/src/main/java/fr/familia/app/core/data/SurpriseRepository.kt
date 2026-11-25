package fr.familia.app.core.data

import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.Surprise
import kotlinx.coroutines.flow.Flow

/**
 * The surprises (brief Cadeaux et Surprise du 25/11): surprises/{surpriseId}, read by its members
 * only, and openSurprises/{surpriseId}, its title for the family but the beneficiary. See
 * docs/data-model.md.
 */
interface SurpriseRepository {
    /** The surprises [memberId] is in, never one they are the beneficiary of. */
    fun observeMine(memberId: String): Flow<List<Surprise>>

    /** One surprise; null when it is gone or [memberId] is not in it any more. */
    fun observeSurprise(id: String): Flow<Surprise?>

    /** The surprises in preparation for [beneficiaryId], to join one rather than start a second. */
    fun observeOpenFor(beneficiaryId: String): Flow<List<OpenSurprise>>

    fun newSurpriseId(): String

    /** Writes the surprise and its open line together. */
    suspend fun saveSurprise(surprise: Surprise, isNew: Boolean)

    suspend fun deleteSurprise(id: String)

    /** « Rejoindre »: adds [memberId], and only them, to a surprise they cannot read yet. */
    suspend fun joinSurprise(id: String, memberId: String)

    /** The proposals of a surprise: the wishes someone already noted and the ideas added. */
    fun observeProposals(surpriseId: String): Flow<List<SurpriseProposal>>

    /** My own notes in a surprise, by proposal: nobody else reads them. */
    fun observeMyRatings(surpriseId: String, memberId: String): Flow<Map<String, Int>>

    /**
     * Notes a proposal from 1 to 5; null takes my note back. The proposal of a wish is written with
     * it the first time, from [proposal].
     */
    suspend fun rate(surpriseId: String, proposal: SurpriseProposal, memberId: String, stars: Int?)

    /** « Ajoutée »: an idea of my notebook joins the surprise. */
    suspend fun addIdea(surpriseId: String, proposal: SurpriseProposal)

    /** « Privée » again: the idea leaves the surprise, and stays in my notebook. */
    suspend fun removeIdea(surpriseId: String, proposalId: String)

    /** « Déjà offert à X »: what the person already received, never read by them. */
    fun observeHistory(beneficiaryId: String): Flow<List<GiftHistoryItem>>
}
