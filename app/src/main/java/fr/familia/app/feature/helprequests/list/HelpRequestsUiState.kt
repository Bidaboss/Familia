package fr.familia.app.feature.helprequests.list

import fr.familia.app.core.model.HelpRequest
import java.time.LocalDate

/** One card of the tab. [helpedName] is the child helped, null when the author asks for themselves. */
data class HelpRequestRow(
    val request: HelpRequest,
    val helpedName: String?,
    val isMine: Boolean,
    val iAmVolunteer: Boolean,
    val iDeclined: Boolean,
)

sealed interface HelpRequestsUiState {
    data object Loading : HelpRequestsUiState
    data object Error : HelpRequestsUiState

    /** Open requests first, then the ones already taken, each by date (récit 15). */
    data class Success(
        val open: List<HelpRequestRow>,
        val taken: List<HelpRequestRow>,
        val today: LocalDate,
    ) : HelpRequestsUiState {
        val isEmpty: Boolean get() = open.isEmpty() && taken.isEmpty()
    }
}
