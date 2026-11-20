package fr.familia.app.feature.helprequests.detail

import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Household
import java.time.LocalDate

/** Why the last action did not go through. */
sealed interface HelpActionError {
    /** Someone was quicker: « déjà prise par [name] » (spec, cas limites). */
    data class AlreadyTaken(val volunteerName: String?) : HelpActionError
    /** « Je m'en occupe » needs the network, so that there are never two volunteers. */
    data object NeedsNetwork : HelpActionError
    data object Failed : HelpActionError
}

sealed interface HelpRequestDetailUiState {
    data object Loading : HelpRequestDetailUiState

    /** Deleted, cancelled, or no longer shared with me. */
    data object Missing : HelpRequestDetailUiState

    data class Success(
        val request: HelpRequest,
        val today: LocalDate,
        /** The child helped, null when the author asks for themselves. */
        val helpedName: String?,
        val isMine: Boolean,
        val myFirstName: String,
        val warnedNames: List<String>,
        /** The first name of the only person warned who answered « Je ne peux pas ». */
        val declinedName: String?,
        val canVolunteer: Boolean,
        val canDecline: Boolean,
        val canWithdraw: Boolean,
        val canManage: Boolean,
        val canThank: Boolean,
        /** The whole family reads it (author and people warned): only then may it leave Familia. */
        val seenByWholeFamily: Boolean = false,
        /** For « Chez un foyer » and its « Itinéraire » (Kévin 20/11). */
        val households: List<Household> = emptyList(),
        val isBusy: Boolean = false,
        val error: HelpActionError? = null,
    ) : HelpRequestDetailUiState
}
