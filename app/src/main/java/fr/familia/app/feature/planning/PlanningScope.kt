package fr.familia.app.feature.planning

import android.content.Context
import androidx.core.content.edit

/** « Toute la famille » (everything, the default) or « Mon foyer » (PRD, carte AVIS-8). */
enum class PlanningScope { Family, Household }

/** The last choice of the planning filter, kept on this phone only (PRD, carte AVIS-8). */
class PlanningScopePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var scope: PlanningScope
        get() = if (prefs.getString(KEY, null) == PlanningScope.Household.name) PlanningScope.Household else PlanningScope.Family
        set(value) = prefs.edit { putString(KEY, value.name) }

    private companion object {
        const val KEY = "planningScope"
    }
}
