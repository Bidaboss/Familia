package fr.familia.app.core.data

/**
 * What a member leaves on the phone besides their account: the links and the cache of the photos
 * they saw, the PDF they last made (note C de #264). A phone can be shared, so when another member
 * signs in, [wipe] removes all of it first. The same member signing in again keeps everything.
 */
class LocalTraces(private val owner: TraceOwner, private val wipe: () -> Unit) {

    /** Called once a member is signed in; slow (files), so off the main thread. */
    fun signedIn(memberId: String) {
        val previous = owner.get()
        if (previous == memberId) return
        if (previous != null) wipe()
        owner.put(memberId)
    }
}

/** Who the traces on this phone belong to: the preferences of the app, or a variable in tests. */
interface TraceOwner {
    fun get(): String?
    fun put(memberId: String)
}

class PreferencesTraceOwner(private val prefs: android.content.SharedPreferences) : TraceOwner {
    override fun get(): String? = prefs.getString(KEY, null)
    override fun put(memberId: String) {
        prefs.edit().putString(KEY, memberId).apply()
    }

    private companion object {
        const val KEY = "memberId"
    }
}
