package fr.familia.app.feature.onboarding

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the tutorial was seen on this phone (spec: kept on the phone only, so a new phone shows
 * it again). Nothing is stored in Firestore.
 */
class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    val seen: Boolean get() = prefs.getBoolean(SEEN_KEY, false)

    fun markSeen() = prefs.edit { putBoolean(SEEN_KEY, true) }

    private companion object {
        const val SEEN_KEY = "onboardingSeen"
    }
}
