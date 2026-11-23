package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.model.TripStep
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date

/**
 * Trips live in `trips/{tripId}` and their steps in `trips/{tripId}/steps/{stepId}`. See
 * docs/data-model.md for the fields and the rights.
 */
class FirestoreTripRepository(private val db: FirebaseFirestore) : TripRepository {

    override fun observeFollowedTrips(memberId: String): Flow<List<Trip>> = callbackFlow {
        // The rules only let a member read the trips they follow, so the query must say so.
        val registration = db.collection(TRIPS)
            .whereArrayContains("readerIds", memberId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toTrip() }.sortedWith(tripOrder))
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeTrip(id: String): Flow<Trip?> = callbackFlow {
        val registration = db.collection(TRIPS).document(id).addSnapshotListener { snapshot, error ->
            when {
                // Unticked from the followers meanwhile: for this member, the trip is gone.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(null)
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.takeIf { it.exists() }?.toTrip())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeSteps(tripId: String): Flow<List<TripStep>> = callbackFlow {
        val registration = steps(tripId).addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyList())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toStep() }.sortedWith(stepOrder))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getTrip(id: String): Trip? =
        db.collection(TRIPS).document(id).get().await().takeIf { it.exists() }?.toTrip()

    override suspend fun getStep(tripId: String, stepId: String): TripStep? =
        steps(tripId).document(stepId).get().await().takeIf { it.exists() }?.toStep()

    override fun newTripId(): String = db.collection(TRIPS).document().id

    override fun newStepId(tripId: String): String = steps(tripId).document().id

    override suspend fun saveTrip(trip: Trip, isNew: Boolean) {
        val ref = db.collection(TRIPS).document(trip.id)
        val fields = mapOf(
            "title" to trip.title,
            "startDate" to trip.startDate.toString(),
            "endDate" to trip.endDate.toString(),
            "participantIds" to trip.participantIds,
            "readerIds" to trip.readerIds,
            "landscape" to trip.landscape.key,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            ref.set(
                fields + mapOf(
                    "creatorId" to trip.creatorId,
                    "creatorName" to trip.creatorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteTrip(id: String) {
        db.collection(TRIPS).document(id).delete().await()
    }

    override suspend fun saveStep(tripId: String, step: TripStep, isNew: Boolean, remindAt: Instant?) {
        val ref = steps(tripId).document(step.id)
        val fields = mapOf(
            "type" to step.type.firestoreValue,
            "place" to step.place,
            "departDate" to step.departDate.toString(),
            "departTime" to step.departTime?.hhmm(),
            "arriveDate" to step.arriveDate?.toString(),
            "arriveTime" to step.arriveTime?.hhmm(),
            "note" to step.note,
            "lat" to step.lat,
            "lng" to step.lng,
            "remindAt" to remindAt?.let { Timestamp(Date.from(it)) },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            ref.set(fields + mapOf("arrivedAt" to null, "arrivedBy" to null, "arrivedByName" to null)).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteStep(tripId: String, stepId: String) {
        steps(tripId).document(stepId).delete().await()
    }

    override suspend fun markArrived(tripId: String, stepId: String, me: Member) {
        steps(tripId).document(stepId).update(
            mapOf(
                "arrivedAt" to FieldValue.serverTimestamp(),
                "arrivedBy" to me.id,
                "arrivedByName" to me.firstName,
                "remindAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun alertFamily(tripId: String, me: Member) {
        try {
            db.collection(TRIPS).document(tripId).collection(ALERTS).document(me.id).set(
                mapOf(
                    "authorId" to me.id,
                    "authorName" to me.firstName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw AlertTooSoonException(e)
            throw e
        }
    }

    override suspend fun clearArrival(tripId: String, stepId: String) {
        steps(tripId).document(stepId).update(
            mapOf(
                "arrivedAt" to null,
                "arrivedBy" to null,
                "arrivedByName" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override fun observeItems(tripId: String): Flow<List<TripItem>> = callbackFlow {
        val registration = items(tripId).addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyList())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toItem() }.sortedWith(itemOrder))
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newItemId(tripId: String): String = items(tripId).document().id

    override suspend fun saveItem(tripId: String, item: TripItem, isNew: Boolean) {
        val fields = mapOf(
            "label" to item.label,
            "assigneeId" to item.assigneeId,
            "assigneeName" to item.assigneeName,
            "done" to item.done,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = items(tripId).document(item.id)
        // An edit never sends « done »: a tick made meanwhile by someone else stays.
        if (isNew) ref.set(fields).await() else ref.update(fields - "done").await()
    }

    override suspend fun setItemAssignee(tripId: String, itemId: String, expectedId: String?, assignee: Member?): Boolean {
        val ref = items(tripId).document(itemId)
        return db.runTransaction { tx ->
            val line = tx.get(ref)
            if (!line.exists() || line.getString("assigneeId") != expectedId) return@runTransaction false
            tx.update(ref, mapOf("assigneeId" to assignee?.id, "assigneeName" to assignee?.firstName, "updatedAt" to FieldValue.serverTimestamp()))
            true
        }.await()
    }

    override suspend fun setItemDone(tripId: String, itemId: String, done: Boolean) {
        items(tripId).document(itemId).update(mapOf("done" to done, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun deleteItem(tripId: String, itemId: String) {
        items(tripId).document(itemId).delete().await()
    }

    private fun steps(tripId: String) = db.collection(TRIPS).document(tripId).collection(STEPS)

    private fun items(tripId: String) = db.collection(TRIPS).document(tripId).collection(ITEMS)

    private fun DocumentSnapshot.toItem(): TripItem? {
        val label = getString("label") ?: return null
        return TripItem(
            id = id,
            label = label,
            assigneeId = getString("assigneeId"),
            assigneeName = getString("assigneeName"),
            done = getBoolean("done") == true,
        )
    }

    /** Null for a document that does not have the expected shape (never shown rather than crash). */
    private fun DocumentSnapshot.toTrip(): Trip? {
        val start = getString("startDate")?.toDay() ?: return null
        val end = getString("endDate")?.toDay() ?: return null
        return Trip(
            id = id,
            title = getString("title").orEmpty(),
            startDate = start,
            endDate = end,
            participantIds = stringList("participantIds"),
            readerIds = stringList("readerIds"),
            creatorId = getString("creatorId").orEmpty(),
            creatorName = getString("creatorName").orEmpty(),
            landscape = TripLandscape.of(getString("landscape")),
        )
    }

    private fun DocumentSnapshot.toStep(): TripStep? {
        val type = StepType.fromFirestore(getString("type")) ?: return null
        val depart = getString("departDate")?.toDay() ?: return null
        val arrivedBy = getString("arrivedBy")
        return TripStep(
            id = id,
            type = type,
            place = getString("place").orEmpty(),
            departDate = depart,
            departTime = getString("departTime")?.toTime(),
            arriveDate = getString("arriveDate")?.toDay(),
            arriveTime = getString("arriveTime")?.toTime(),
            note = getString("note"),
            lat = getDouble("lat"),
            lng = getDouble("lng"),
            // A « bien arrivé » tapped offline has no server time yet: the estimate shows it at once.
            arrival = arrivedBy?.let {
                TripArrival(
                    at = getTimestamp("arrivedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant(),
                    memberId = it,
                    name = getString("arrivedByName").orEmpty(),
                )
            },
        )
    }

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun String.toDay(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private fun String.toTime(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

    // Always HH:mm, as the rules expect (LocalTime.toString adds seconds when they are not zero).
    private fun LocalTime.hhmm(): String = LocalTime.of(hour, minute).toString()

    private companion object {
        const val TRIPS = "trips"
        const val STEPS = "steps"
        const val ITEMS = "items"
        const val ALERTS = "alerts"
    }
}
