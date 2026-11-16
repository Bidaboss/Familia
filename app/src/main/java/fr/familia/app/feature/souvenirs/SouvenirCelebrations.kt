package fr.familia.app.feature.souvenirs

import android.content.Context
import androidx.core.content.edit

/**
 * The souvenirs whose « Bravo » was already shown on this phone (récit 34: once). Kept on the
 * phone only, like the tutorial: nothing is written in Firestore, and the rules stay as they are.
 */
interface SouvenirCelebrations {
    fun celebrated(memberId: String): Set<String>
    fun markCelebrated(memberId: String, keys: Set<String>)
}

class SouvenirCelebrationPreferences(context: Context) : SouvenirCelebrations {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    override fun celebrated(memberId: String): Set<String> = prefs.getStringSet(key(memberId), null).orEmpty().toSet()

    override fun markCelebrated(memberId: String, keys: Set<String>) =
        prefs.edit { putStringSet(key(memberId), celebrated(memberId) + keys) }

    private fun key(memberId: String) = "souvenirsCelebrated_$memberId"
}
