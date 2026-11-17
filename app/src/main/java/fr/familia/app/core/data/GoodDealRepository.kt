package fr.familia.app.core.data

import fr.familia.app.core.model.GoodDeal
import kotlinx.coroutines.flow.Flow

/** The bons plans et bonnes adresses of the family (PRD, récit 45). See docs/data-model.md. */
interface GoodDealRepository {
    /** Every post, ended bons plans included, updated live. */
    fun observeAll(): Flow<List<GoodDeal>>

    fun observe(id: String): Flow<GoodDeal?>

    suspend fun get(id: String): GoodDeal?

    fun newId(): String

    /** Writes [deal]; [isNew] sets its creation time and its empty lists of names. */
    suspend fun save(deal: GoodDeal, isNew: Boolean)

    suspend fun delete(id: String)

    /** « Ça m'intéresse » on a bon plan: adds or removes [memberId], and only that. */
    suspend fun setInterested(id: String, memberId: String, interested: Boolean)

    /** « Je recommande aussi » on a bonne adresse: adds or removes [memberId], and only that. */
    suspend fun setRecommended(id: String, memberId: String, recommended: Boolean)
}
