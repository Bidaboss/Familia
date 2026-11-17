package fr.familia.app.feature.helprequests

import fr.familia.app.core.data.AlreadyTakenException
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A fictional request by [author], for themselves unless [forMemberId] says otherwise. */
fun helpRequest(
    id: String,
    author: Member,
    notifyIds: List<String>,
    date: LocalDate = LocalDate.of(2026, 12, 2),
    forMemberId: String = author.id,
    status: HelpStatus = HelpStatus.Open,
    volunteer: Member? = null,
) = HelpRequest(
    id, HelpKind.Childcare, "Garde $id", date, null, null, null, null,
    authorId = author.id, authorName = author.firstName, forMemberId = forMemberId, notifyIds = notifyIds,
    status = status, volunteerId = volunteer?.id, volunteerName = volunteer?.firstName,
)

class FakeHelpRequests(
    initial: List<HelpRequest> = emptyList(),
    private val failWith: Exception? = null,
    /** Makes « Je m'en occupe » hang, like a transaction without network. */
    private val hangTake: Boolean = false,
) : HelpRequestRepository {
    val requests = MutableStateFlow(initial)
    val saved = mutableListOf<Pair<HelpRequest, Boolean>>()

    override fun observeMine(memberId: String): Flow<List<HelpRequest>> =
        requests.map { list -> list.filter { it.authorId == memberId || memberId in it.notifyIds } }

    override fun observeRequest(id: String): Flow<HelpRequest?> = requests.map { list -> list.firstOrNull { it.id == id } }

    override fun newRequestId(): String = "r-new"

    override suspend fun saveRequest(request: HelpRequest, isNew: Boolean) {
        failWith?.let { throw it }
        saved += request to isNew
        requests.value = requests.value.filter { it.id != request.id } + request
    }

    override suspend fun take(id: String, volunteerId: String, volunteerName: String, note: String?) {
        failWith?.let { throw it }
        if (hangTake) awaitCancellation()
        val current = requests.value.first { it.id == id }
        if (current.status != HelpStatus.Open) throw AlreadyTakenException(current.volunteerName)
        change(id) { it.copy(status = HelpStatus.Taken, volunteerId = volunteerId, volunteerName = volunteerName, volunteerNote = note) }
    }

    override suspend fun withdraw(id: String) = change(id) { it.copy(status = HelpStatus.Open, volunteerId = null, volunteerName = null, volunteerNote = null) }

    override suspend fun setVolunteerNote(id: String, note: String?) = change(id) { it.copy(volunteerNote = note) }

    override suspend fun decline(id: String, memberId: String) = change(id) { it.copy(declinedBy = memberId) }
    override suspend fun markUnavailable(id: String, memberId: String) = change(id) { it.copy(unavailableIds = it.unavailableIds + memberId) }

    override suspend fun thank(id: String) = change(id) { it.copy(thankedAt = java.time.Instant.EPOCH) }

    override suspend fun cancel(id: String) = change(id) { it.copy(status = HelpStatus.Cancelled) }

    private fun change(id: String, block: (HelpRequest) -> HelpRequest) {
        failWith?.let { throw it }
        requests.value = requests.value.map { if (it.id == id) block(it) else it }
    }
}
