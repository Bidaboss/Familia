package fr.familia.app.feature.planning

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Member
import fr.familia.app.feature.events.edit.TargetChoice
import java.time.LocalDate

/** One row of the planning, with what the screen needs to show it. */
data class PlanningItem(
    val event: Event,
    val target: TargetChoice,
    val authorName: String,
    /** The author, for their avatar; null once they have left the family (the name is then shown). */
    val author: Member? = null,
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
)

sealed interface PlanningUiState {
    data object Loading : PlanningUiState
    /** [today] lets the screen write "Aujourd'hui" and "Demain" in the day headers. */
    data class Success(
        val days: List<PlanningDay>,
        val today: LocalDate,
        val scope: PlanningScope = PlanningScope.Family,
    ) : PlanningUiState
    data object Error : PlanningUiState
}
