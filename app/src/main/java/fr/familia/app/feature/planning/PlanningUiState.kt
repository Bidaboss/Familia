package fr.familia.app.feature.planning

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.events.edit.TargetChoice
import java.time.LocalDate

/** One row of the planning, with what the screen needs to show it. */
data class PlanningItem(
    val event: Event,
    val target: TargetChoice,
    val authorName: String,
    /** True when this member may edit or delete it (the event view then shows "Modifier"). */
    val canManage: Boolean,
    /** "Qui vient": members of the family who answered yes, and maybe. */
    val coming: Int = 0,
    val maybe: Int = 0,
)

/** The birthdays and events of one day. Birthdays come first: they last the whole day. */
data class PlanningDay(
    val date: LocalDate,
    val items: List<PlanningItem>,
    val birthdays: List<Birthday> = emptyList(),
    /** Trips (V3) shown on their first day, or today while they are ongoing. */
    val trips: List<PlanningTrip> = emptyList(),
)

/** A trip the member follows, in the planning (PRD, récit 11). */
data class PlanningTrip(val trip: Trip, val participants: List<String>, val ongoing: Boolean)

sealed interface PlanningUiState {
    data object Loading : PlanningUiState
    /** [today] lets the screen write "Aujourd'hui" and "Demain" in the day headers. */
    data class Success(
        val days: List<PlanningDay>,
        val today: LocalDate,
        /** Every day of the trips to come, marked in the month view (PRD, récit 11). */
        val tripDays: Set<LocalDate> = emptySet(),
    ) : PlanningUiState
    data object Error : PlanningUiState
}
