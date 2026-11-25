package fr.familia.app.feature.gifts

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * When this phone last opened each list of wishes, for the « 2 nouvelles » of the Cadeaux tab
 * (présentation R1 bis). Kept on the phone only: nobody else learns what one looked at.
 */
class GiftSeenPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia_gifts_seen", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    /** Epoch milliseconds of the last visit, by list id. */
    val seen: StateFlow<Map<String, Long>> = state

    fun markSeen(listId: String, at: Instant = Instant.now()) {
        val before = state.value[listId]
        prefs.edit {
            putLong(listId, at.toEpochMilli())
            // The visit before, for the « nouveau » of the list just opened (correctif E3).
            if (before != null) putLong(PREVIOUS + listId, before) else remove(PREVIOUS + listId)
        }
        state.value = read()
    }

    /** The visit before the last one: what the list itself counts as new. Null before a second visit. */
    fun previousVisit(listId: String): Long? = prefs.getLong(PREVIOUS + listId, -1L).takeIf { it >= 0 }

    /** The first time the tab shows a list, nothing in it is new yet. */
    fun markFirstSeen(listIds: Collection<String>, at: Instant = Instant.now()) {
        val missing = listIds.filter { it !in state.value }
        if (missing.isEmpty()) return
        prefs.edit { missing.forEach { putLong(it, at.toEpochMilli()) } }
        state.value = read()
    }

    private fun read(): Map<String, Long> =
        prefs.all.filterKeys { !it.startsWith(PREVIOUS) }.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()

    private companion object {
        const val PREVIOUS = "previous:"
    }
}
