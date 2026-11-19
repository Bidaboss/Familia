package fr.familia.app.feature.events.detail

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.edit.TargetChoice
import java.time.LocalDate

/** One person this member may answer for, with their current answer (null: not answered yet). */
/** [pending]: the answer was given without network and leaves by itself when it is back. */
data class Answerer(val member: Member, val status: RsvpStatus?, val isMe: Boolean, val pending: Boolean = false)

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState

    data class Success(
        val event: Event,
        val today: LocalDate,
        val target: TargetChoice,
        val authorName: String,
        /** True when this member may edit or delete the event: the "Modifier" button shows. */
        val canManage: Boolean,
        /** The member first, then their children (PRD, récit 4). */
        val answerers: List<Answerer>,
        /** First names by answer, for the whole family. */
        val coming: List<String>,
        val maybe: List<String>,
        val notComing: List<String>,
        /** Those who come, for the avatars of « N viennent » (DESIGN-5). */
        val comingMembers: List<Member> = emptyList(),
        /** The last answer could not be sent (refused, or an error other than being offline). */
        val sendFailed: Boolean = false,
        /** For « Chez un foyer » (récit 74): the household's name and address as they are now. */
        val households: List<Household> = emptyList(),
    ) : EventDetailUiState

    /** The event was deleted, or never existed. */
    data object Missing : EventDetailUiState

    data object Error : EventDetailUiState
}
