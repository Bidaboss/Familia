package fr.familia.app.core.data

import fr.familia.app.core.model.BirthdayCard
import kotlinx.coroutines.flow.Flow

/** The cards of vœux (récit 36). Screens never talk to Firestore directly. */
interface BirthdayCardRepository {
    /** The cards written to [memberId] for the birthday of [year], oldest first, updated live. */
    fun observeFor(memberId: String, year: Int): Flow<List<BirthdayCard>>

    /** Writes or changes one's own card. */
    suspend fun save(card: BirthdayCard, isNew: Boolean)

    /** Removes one's own card. */
    suspend fun remove(card: BirthdayCard)
}
