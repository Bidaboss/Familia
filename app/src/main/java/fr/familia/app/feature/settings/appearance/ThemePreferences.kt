package fr.familia.app.feature.settings.appearance

import android.content.Context
import androidx.core.content.edit
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.ThemeChoice
import fr.familia.app.core.theme.TitleFont

/**
 * The theme, « Contraste fort » and the two fonts chosen on this phone (récit 113, DESIGN-45 and 46):
 * kept on the phone only, never in Firestore. The color themes of before are read as « Selon la
 * saison » (ThemeChoice.fromKey); a phone that was on « Contraste fort » keeps it, now apart.
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var theme: ThemeChoice
        get() = ThemeChoice.fromKey(prefs.getString(THEME_KEY, null))
        set(value) {
            // « Contraste fort » read from the old key is kept before the key changes.
            val contrast = highContrast
            prefs.edit {
                if (!prefs.contains(HIGH_CONTRAST_KEY)) putBoolean(HIGH_CONTRAST_KEY, contrast)
                putString(THEME_KEY, value.key)
            }
        }

    /** « Contraste fort » (règle 18): plain, without photo, whatever the theme. */
    var highContrast: Boolean
        get() = prefs.getBoolean(HIGH_CONTRAST_KEY, prefs.getString(THEME_KEY, null) == OLD_HIGH_CONTRAST)
        set(value) = prefs.edit { putBoolean(HIGH_CONTRAST_KEY, value) }

    var titleFont: TitleFont
        get() = TitleFont.fromKey(prefs.getString(TITLE_FONT_KEY, null))
        set(value) = prefs.edit { putString(TITLE_FONT_KEY, value.key) }

    var bodyFont: BodyFont
        get() = BodyFont.fromKey(prefs.getString(BODY_FONT_KEY, null))
        set(value) = prefs.edit { putString(BODY_FONT_KEY, value.key) }

    private companion object {
        const val THEME_KEY = "theme"
        const val HIGH_CONTRAST_KEY = "highContrast"

        /** The key of « Contraste fort » when it was one of the 7 themes. */
        const val OLD_HIGH_CONTRAST = "contrast"
        const val TITLE_FONT_KEY = "titleFont"
        const val BODY_FONT_KEY = "bodyFont"
    }
}
