package fr.familia.app.feature.settings.appearance

import android.content.Context
import androidx.core.content.edit
import fr.familia.app.core.theme.FamiliaThemeId

/** The theme chosen on this phone (DESIGN-7): kept on the phone only, never in Firestore. */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var theme: FamiliaThemeId
        get() = FamiliaThemeId.fromKey(prefs.getString(THEME_KEY, null))
        set(value) = prefs.edit { putString(THEME_KEY, value.key) }

    private companion object {
        const val THEME_KEY = "theme"
    }
}
