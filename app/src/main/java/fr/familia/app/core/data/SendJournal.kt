package fr.familia.app.core.data

import java.util.Collections

/** What was created without network, for the sentence that names it if it never left. */
enum class SendKind(val key: String) {
    HelpRequest("help"),
    GoodNews("goodNews"),
    GoodDeal("goodDeal"),
    LendItem("lend"),
    Turn("turn"),
    ;

    companion object {
        fun fromKey(key: String?): SendKind? = entries.firstOrNull { it.key == key }
    }
}

/** One creation on its way: the document [path], what it is, its title, and who made it. */
data class SendEntry(val path: String, val kind: SendKind, val title: String, val memberId: String)

/** What the journal keeps on the phone: the preferences of the app, or a map in tests. */
interface SendStore {
    fun all(): List<SendEntry>
    fun put(entry: SendEntry)
    fun remove(path: String)
}

/**
 * The journal of the creations sent from this phone (note C de #331). Offline, Firestore keeps a
 * write on the phone and sends it later, even after the app was closed. Closed, nobody hears if the
 * server refuses it then: the item vanishes from the cache without a word. So each creation is noted
 * when it starts and forgotten once Firestore answered in this launch (a refusal then is said by its
 * screen). What a former launch left is checked after sign-in ([leftovers]): what the server does not
 * have is said once, with its title, so the person can do it again.
 *
 * [missingOnServer] waits for the writes this phone still holds, then answers which paths the server
 * does not have, or null when it cannot tell (no network): the entries then wait for the next launch.
 */
class SendJournal(
    private val store: SendStore,
    private val missingOnServer: suspend (List<String>) -> Set<String>?,
) {
    private val inFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun started(entry: SendEntry) {
        inFlight += entry.path
        store.put(entry)
    }

    /** Firestore answered in this launch, or the item was removed on purpose. */
    fun settled(path: String) {
        inFlight -= path
        store.remove(path)
    }

    /**
     * The creations of [memberId] that a former launch left and the server never received, each
     * said once. Those of another member on a shared phone are dropped; theirs to know, not ours.
     */
    suspend fun leftovers(memberId: String): List<SendEntry> {
        val old = store.all().filter { it.path !in inFlight }
        old.filter { it.memberId != memberId }.forEach { store.remove(it.path) }
        val mine = old.filter { it.memberId == memberId }
        if (mine.isEmpty()) return emptyList()
        val missing = missingOnServer(mine.map { it.path }) ?: return emptyList()
        // Started again meanwhile in this launch: its own answer will come.
        val checked = mine.filter { it.path !in inFlight }
        checked.forEach { store.remove(it.path) }
        return checked.filter { it.path in missing }
    }
}

/** The journal in the preferences of the app: one key per path, « kind|memberId|title ». */
class PreferencesSendStore(private val prefs: android.content.SharedPreferences) : SendStore {
    override fun all(): List<SendEntry> = prefs.all.mapNotNull { (path, value) ->
        val parts = (value as? String)?.split('|', limit = 3) ?: return@mapNotNull null
        if (parts.size != 3) return@mapNotNull null
        SendKind.fromKey(parts[0])?.let { SendEntry(path, it, parts[2], parts[1]) }
    }

    override fun put(entry: SendEntry) {
        prefs.edit().putString(entry.path, "${entry.kind.key}|${entry.memberId}|${entry.title}").apply()
    }

    override fun remove(path: String) {
        prefs.edit().remove(path).apply()
    }
}
