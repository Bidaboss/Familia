package fr.familia.app.feature.home

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the Accueil module showed the last time the app read config/app. The app opens on the
 * tab read here, at once, without waiting for the network; a change counts from the next opening.
 */
class HomePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var homeOn: Boolean
        get() = prefs.getBoolean(HOME_ON_KEY, false)
        set(value) {
            if (value != homeOn) prefs.edit { putBoolean(HOME_ON_KEY, value) }
        }

    private companion object {
        const val HOME_ON_KEY = "homeTabOn"
    }
}
