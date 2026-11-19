package fr.familia.app.feature.update

import android.content.Context
import androidx.core.content.edit

/**
 * The version whose sheet « Une nouvelle version est prête » was already shown (DESIGN-47, F): the
 * sheet comes once per version, then the dark card stays until the update.
 */
class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var sheetSeen: String?
        get() = prefs.getString(SHEET_SEEN_KEY, null)
        set(value) = prefs.edit { putString(SHEET_SEEN_KEY, value) }

    private companion object {
        const val SHEET_SEEN_KEY = "updateSheetSeen"
    }
}

/** True when the sheet of [update] was not shown yet on this phone. */
fun shouldShowUpdateSheet(update: AppUpdate?, seenVersion: String?): Boolean =
    update != null && update.versionName != seenVersion
