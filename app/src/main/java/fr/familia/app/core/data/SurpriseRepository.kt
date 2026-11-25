package fr.familia.app.core.data

import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.OfferedSurprise
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
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

    /** ✕ of Résultats: out of the list (or back in); it stays in the wishes or ideas of the person. */
    suspend fun setRemoved(surpriseId: String, proposal: SurpriseProposal, removed: Boolean)

    /** What is taken in a surprise, by proposal. */
    fun observeTakes(surpriseId: String): Flow<List<SurpriseTake>>

    /**
     * « Je le prends » or « Cadeau commun » of [goal] euros. The proposal of a wish never noted is
     * written with it.
     */
    suspend fun take(surpriseId: String, proposal: SurpriseProposal, memberId: String, kind: TakeKind, goal: Int?)

    /** « Je ne le prends plus », or the joint gift closed by its owner: its parts go too. */
    suspend fun release(surpriseId: String, proposalId: String)

    /** My parts in the joint gifts of a surprise, by proposal. */
    fun observeMyShares(surpriseId: String, memberId: String): Flow<Map<String, Int>>

    /** The parts of my joint gift, by member: only its owner reads them. */
    fun observeShares(surpriseId: String, proposalId: String): Flow<Map<String, Int>>

    /** My part in whole euros; null takes it back. */
    suspend fun setShare(surpriseId: String, proposalId: String, memberId: String, amount: Int?)

    /** Who validated, who does not take part. */
    fun observeMemberRows(surpriseId: String): Flow<List<SurpriseMemberRow>>

    /** « Valider ma participation », or take it back. */
    suspend fun setValidated(surpriseId: String, memberId: String, validated: Boolean)

    /** « Je ne participe pas »: final, only the creator invites again. */
    suspend fun decline(surpriseId: String, memberId: String)

    /** The reminders of this surprise on mute for [memberId] (réglage personnel, brief § 9). */
    suspend fun setMuted(surpriseId: String, memberId: String, muted: Boolean)

    /** « Réinviter », by the creator alone: the line of [memberId] goes, with its « Je ne participe pas ». */
    suspend fun reinvite(surpriseId: String, memberId: String)

    /**
     * « C'est offert », from the day itself: one member confirms it for all. The server does the
     * rest (« Déjà offert à X », the notebooks, the end of the reminders).
     */
    suspend fun markOffered(surpriseId: String, memberId: String)

    /**
     * Administration > « Surprises offertes récemment », for the admin principal [adminId] alone,
     * never the ones they were given.
     */
    fun observeOffered(adminId: String): Flow<List<OfferedSurprise>>

    /** « Annuler C'est offert »: asked once, the server puts the surprise back and tells the members. */
    suspend fun askCancelOffered(surpriseId: String)
}
