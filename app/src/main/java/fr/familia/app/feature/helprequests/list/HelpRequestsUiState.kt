package fr.familia.app.feature.helprequests.list

import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.feature.helprequests.todo.HelpRequestTodo
import java.time.LocalDate

/** One card of the tab. [helpedName] is the child helped, null when the author asks for themselves. */
data class HelpRequestRow(
    val request: HelpRequest,
    val helpedName: String?,
    val isMine: Boolean,
    val iAmVolunteer: Boolean,
    val iDeclined: Boolean,
    /** Who took it, for the avatar of « Déjà pris en charge » (R1 bis). */
    val volunteer: Member? = null,
)

sealed interface HelpRequestsUiState {
    data object Loading : HelpRequestsUiState
    data object Error : HelpRequestsUiState

    /** Open requests first, then the ones already taken, each by date (récit 15). */
    data class Success(
        val open: List<HelpRequestRow>,
        val taken: List<HelpRequestRow>,
        val today: LocalDate,
        /** The open requests that wait for me, for « On a besoin de toi » (R1 bis, §4). */
        val forMe: List<HelpRequestTodo> = emptyList(),
    ) : HelpRequestsUiState {
        /** R1 bis: my own open requests, under « Mes demandes ». */
        val mine: List<HelpRequestRow> get() = open.filter { it.isMine }

        /** R1 bis: the open requests of others I said no to, under « Pas dispo cette fois ». */
        val saidNo: List<HelpRequestRow>
            get() = forMe.map { it.request.id }.toSet().let { ids -> open.filter { !it.isMine && it.request.id !in ids } }

        val isEmpty: Boolean get() = open.isEmpty() && taken.isEmpty() && forMe.isEmpty()
    }
}
