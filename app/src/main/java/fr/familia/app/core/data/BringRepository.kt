package fr.familia.app.core.data

import fr.familia.app.core.model.BringItem
import kotlinx.coroutines.flow.Flow

/** events/{eventId}/bring/{itemId} (PRD, récit 4 bis). See docs/data-model.md. */
interface BringRepository {
    /** The lines of an event, in the order they were added, updated live. */
    fun observe(eventId: String): Flow<List<BringItem>>

    /** Adds a line, taken at once by [authorId] when [take] is set, with the recipe [recipeId] if any. */
    suspend fun add(eventId: String, label: String, authorId: String, authorName: String, take: Boolean, recipeId: String? = null)

    suspend fun take(eventId: String, itemId: String, memberId: String, firstName: String)

    suspend fun release(eventId: String, itemId: String)

    suspend fun delete(eventId: String, itemId: String)
}
