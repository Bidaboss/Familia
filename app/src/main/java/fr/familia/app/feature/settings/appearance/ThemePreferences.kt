package fr.familia.app.feature.settings.appearance

import android.content.Context
import androidx.core.content.edit
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.TitleFont

/**
 * The theme and the two fonts chosen on this phone (DESIGN-7, DESIGN-45 and 46): kept on the phone
 * only, never in Firestore. A theme removed on 18/11 is read as the closest one (FamiliaThemeId.fromKey).
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var theme: FamiliaThemeId
        get() = FamiliaThemeId.fromKey(prefs.getString(THEME_KEY, null))
        set(value) = prefs.edit { putString(THEME_KEY, value.key) }

    var titleFont: TitleFont
        get() = TitleFont.fromKey(prefs.getString(TITLE_FONT_KEY, null))
        set(value) = prefs.edit { putString(TITLE_FONT_KEY, value.key) }

    var bodyFont: BodyFont
        get() = BodyFont.fromKey(prefs.getString(BODY_FONT_KEY, null))
        set(value) = prefs.edit { putString(BODY_FONT_KEY, value.key) }

    private companion object {
        const val THEME_KEY = "theme"
        const val TITLE_FONT_KEY = "titleFont"
        const val BODY_FONT_KEY = "bodyFont"
    }
}
