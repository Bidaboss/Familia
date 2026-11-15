package fr.familia.app.core.data

import fr.familia.app.core.model.HelpRequest
import kotlinx.coroutines.flow.Flow

/** Access to the « coups de main ». Screens never talk to Firestore directly. */
interface HelpRequestRepository {
    /** The requests [memberId] asked for or was warned about, updated live (offline cache too). */
    fun observeMine(memberId: String): Flow<List<HelpRequest>>

    /** One request, updated live; null once deleted, out of reach or malformed. */
    fun observeRequest(id: String): Flow<HelpRequest?>

    /** A fresh id for a request that is not saved yet. */
    fun newRequestId(): String

    /** Creates the request, or updates what its author may change when [isNew] is false. */
    suspend fun saveRequest(request: HelpRequest, isNew: Boolean)

    /**
     * « Je m'en occupe », in a transaction on the server: throws [AlreadyTakenException] when
     * someone was quicker. Needs the network, so that there are never two volunteers.
     */
    suspend fun take(id: String, volunteerId: String, volunteerName: String)

    /** The volunteer withdraws; the request is open again. */
    suspend fun withdraw(id: String)

    /** « Je ne peux pas », by the only person warned. */
    suspend fun decline(id: String, memberId: String)

    /** « Merci », once. */
    suspend fun thank(id: String)

    suspend fun cancel(id: String)
}

/** Someone else took the request first, or it is no longer open. */
class AlreadyTakenException(val volunteerName: String?) : Exception()
