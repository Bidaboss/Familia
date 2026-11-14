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
            "targetMemberId" to when (val t = event.target) {
                is EventTarget.ForSelf -> t.memberId
                is EventTarget.ForChild -> t.memberId
                else -> null
            },
            "householdId" to (event.target as? EventTarget.ForHousehold)?.householdId,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            ref.set(
                fields + mapOf(
                    "authorId" to event.authorId,
                    "authorName" to event.authorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteEvent(id: String) {
        db.collection(EVENTS).document(id).delete().await()
    }

    /** Null for a document that does not have the expected shape (never shown rather than crash). */
    private fun DocumentSnapshot.toEvent(): Event? {
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val memberId = getString("targetMemberId")
        val target = when (getString("targetType")) {
            "self" -> memberId?.let { EventTarget.ForSelf(it) }
            "child" -> memberId?.let { EventTarget.ForChild(it) }
            "household" -> getString("householdId")?.let { EventTarget.ForHousehold(it) }
            "family" -> EventTarget.ForFamily
            else -> null
        } ?: return null
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
        )
    }

    private fun EventTarget.type() = when (this) {
        is EventTarget.ForSelf -> "self"
        is EventTarget.ForChild -> "child"
        is EventTarget.ForHousehold -> "household"
        EventTarget.ForFamily -> "family"
    }

    private companion object {
        const val EVENTS = "events"
    }
}
