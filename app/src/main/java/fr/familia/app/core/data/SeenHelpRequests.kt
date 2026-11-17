package fr.familia.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the seen requests are kept: the preferences of the app, or a variable in tests. */
interface SeenStore {
    fun load(memberId: String): Set<String>
    fun save(memberId: String, ids: Set<String>)
}

/**
 * The coups de main [memberId] already saw in the « Coups de main » tab (retour [BUG] du 18/11):
 * the badge of the Entraide tab counts only the others. Kept on this phone only, per member.
 */
class SeenHelpRequests(private val store: SeenStore, private val memberId: String) {
    private val ids = MutableStateFlow(store.load(memberId))
    val seen: StateFlow<Set<String>> = ids.asStateFlow()

    fun markSeen(seenIds: Collection<String>) = change { it + seenIds }

    /** A request that no longer needs me leaves the set: open again, it rings again. */
    fun forget(goneIds: Collection<String>) = change { it - goneIds.toSet() }

    private fun change(block: (Set<String>) -> Set<String>) {
        val before = ids.value
        // Keeps the last ones only: a request leaves the tab the day after its date.
        val after = block(before).toList().takeLast(MAX).toSet()
        if (after == before) return
        ids.value = after
        store.save(memberId, after)
    }

    private companion object {
        const val MAX = 200
    }
}

class PreferencesSeenStore(private val prefs: android.content.SharedPreferences) : SeenStore {
    override fun load(memberId: String): Set<String> = prefs.getStringSet(memberId, null)?.toSet().orEmpty()
    override fun save(memberId: String, ids: Set<String>) {
        prefs.edit().putStringSet(memberId, ids).apply()
    }
}
