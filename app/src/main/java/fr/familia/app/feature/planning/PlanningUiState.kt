package fr.familia.app.feature.planning

import fr.familia.app.core.model.Event
import fr.familia.app.feature.events.edit.TargetChoice
import java.time.LocalDate

/** One row of the planning, with what the screen needs to show it. */
data class PlanningItem(
    val event: Event,
    val target: TargetChoice,
    val authorName: String,
    /** True when this member may edit or delete it: the row then opens the form. */
    val canManage: Boolean,
)

/** The events of one day. */
data class PlanningDay(val date: LocalDate, val items: List<PlanningItem>)

sealed interface PlanningUiState {
    data object Loading : PlanningUiState
    data class Success(val days: List<PlanningDay>) : PlanningUiState
    data object Error : PlanningUiState
}
