package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/** Events live in `events/{eventId}`. See docs/data-model.md for the fields and the rights. */
class FirestoreEventRepository(private val db: FirebaseFirestore) : EventRepository {

    override fun observeUpcoming(from: LocalDate): Flow<List<Event>> = callbackFlow {
        // Dates are stored as AAAA-MM-JJ, so text order is date order.
        val registration = db.collection(EVENTS)
            .whereGreaterThanOrEqualTo("date", from.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toEvent() }.sortedWith(eventOrder))
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeOnDays(days: List<LocalDate>): Flow<List<Event>> = callbackFlow {
        val registration = db.collection(EVENTS)
            .whereIn("date", days.take(MAX_DAYS).map { it.toString() })
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toEvent() })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getEvent(id: String): Event? =
        db.collection(EVENTS).document(id).get().await().takeIf { it.exists() }?.toEvent()

    override fun observeEvent(id: String): Flow<Event?> = callbackFlow {
        val registration = db.collection(EVENTS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.takeIf { it.exists() }?.toEvent())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newEventId(): String = db.collection(EVENTS).document().id

    override suspend fun saveEvent(event: Event, isNew: Boolean) {
        val ref = db.collection(EVENTS).document(event.id)
        val fields = mapOf(
            "title" to event.title,
            "date" to event.date.toString(),
            "time" to event.time?.let { LocalTime.of(it.hour, it.minute).toString() }, // Always HH:mm.
            "place" to event.place,
            "note" to event.note,
            "targetType" to event.target.type(),
            "targetMemberId" to event.target.targetMemberId(),
            "householdId" to event.target.targetHouseholdId(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            // Left out when there is none, so the event fits the rules of before the cover too.
            val cover = event.coverPhoto?.let { mapOf("coverPhoto" to it) }.orEmpty()
            ref.set(
                fields + cover + mapOf(
                    "authorId" to event.authorId,
                    "authorName" to event.authorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            // Removing a field that is not there changes nothing, under the old rules too. An event of a
            // series changed by hand is « Cette fois seulement » (récit 54): the series leaves it be.
            val detached = if (event.seriesId != null) mapOf("detached" to true) else emptyMap()
            ref.update(fields + detached + ("coverPhoto" to (event.coverPhoto ?: FieldValue.delete()))).await()
        }
    }

    override suspend fun deleteEvent(id: String) {
        db.collection(EVENTS).document(id).delete().await()
    }

    /** Null for a document that does not have the expected shape (never shown rather than crash). */
    private fun DocumentSnapshot.toEvent(): Event? {
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val target = readTarget() ?: return null
        return Event(
            id = id,
            title = getString("title").orEmpty(),
            date = date,
            time = getString("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            place = getString("place"),
            note = getString("note"),
            target = target,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            coverPhoto = getString("coverPhoto"),
            seriesId = getString("seriesId"),
            occurrence = getString("occurrence")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    private companion object {
        const val EVENTS = "events"

        /** A whereIn query takes 30 values at most. */
        const val MAX_DAYS = 30
    }
}

/** How an event or a series names who it is for (targetType, targetMemberId, householdId). */
internal fun EventTarget.type() = when (this) {
    is EventTarget.ForSelf -> "self"
    is EventTarget.ForChild -> "child"
    is EventTarget.ForHousehold -> "household"
    EventTarget.ForFamily -> "family"
}

internal fun EventTarget.targetMemberId(): String? = when (this) {
    is EventTarget.ForSelf -> memberId
    is EventTarget.ForChild -> memberId
    else -> null
}

internal fun EventTarget.targetHouseholdId(): String? = (this as? EventTarget.ForHousehold)?.householdId

internal fun DocumentSnapshot.readTarget(): EventTarget? {
    val memberId = getString("targetMemberId")
    return when (getString("targetType")) {
        "self" -> memberId?.let { EventTarget.ForSelf(it) }
        "child" -> memberId?.let { EventTarget.ForChild(it) }
        "household" -> getString("householdId")?.let { EventTarget.ForHousehold(it) }
        "family" -> EventTarget.ForFamily
        else -> null
    }
}
