package fr.familia.app.core.data

import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import kotlinx.coroutines.flow.Flow

/**
 * The private gift notebook of a member (PRD, récit 9 bis). Only that member may read it: never ask
 * for the notebook of anyone else, the rules refuse it.
 */
interface NotebookRepository {
    fun observeEntries(memberId: String): Flow<List<NotebookEntry>>

    fun newEntryId(memberId: String): String

    /** Creates or replaces a private idea. */
    suspend fun saveIdea(memberId: String, entry: NotebookEntry, isNew: Boolean)

    /** What the member follows on any entry, including a reservation taken up. */
    suspend fun follow(memberId: String, entryId: String, status: GiftStatus, occasion: NotebookOccasion, note: String?)

    /** Removes a private idea; a reservation leaves the notebook when it is released. */
    suspend fun deleteIdea(memberId: String, entryId: String)
}
