package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.familia.app.core.model.EventReminders
import fr.familia.app.core.model.ReminderOffset
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * members/{id}/reminders/{eventId | series-{seriesId}} and settings/notifications.defaultReminders
 * (PRD rév. 248, récit 56). Private: only the member reads them, so « Voir comme » has none.
 */
interface EventReminderRepository {
    fun observe(memberId: String, eventId: String, seriesId: String?): Flow<EventReminders>

    fun observeDefaults(memberId: String): Flow<Set<ReminderOffset>>

    /** On this event only: an empty set is « pas de rappel », even if its series or the defaults have some. */
    suspend fun setForEvent(memberId: String, eventId: String, offsets: Set<ReminderOffset>)

    /** On every occurrence of a series; the choice of a single occurrence is taken back. */
    suspend fun setForSeries(memberId: String, seriesId: String, eventId: String, offsets: Set<ReminderOffset>)

    suspend fun setDefaults(memberId: String, offsets: Set<ReminderOffset>)
}

class FirestoreEventReminderRepository(private val db: FirebaseFirestore) : EventReminderRepository {

    private fun reminders(memberId: String) = db.collection("members").document(memberId).collection("reminders")
    private fun settings(memberId: String) = db.collection("members").document(memberId).collection("settings").document("notifications")

    private fun observeOffsets(memberId: String, id: String): Flow<Set<ReminderOffset>?> = callbackFlow {
        val registration = reminders(memberId).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(if (snapshot?.exists() == true) ReminderOffset.fromKeys(snapshot.get("offsets") as? List<*>) else null)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeDefaults(memberId: String): Flow<Set<ReminderOffset>> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(ReminderOffset.fromKeys(snapshot?.get("defaultReminders") as? List<*>))
        }
        awaitClose { registration.remove() }
    }

    override fun observe(memberId: String, eventId: String, seriesId: String?): Flow<EventReminders> = combine(
        observeOffsets(memberId, eventId),
        if (seriesId != null) observeOffsets(memberId, "series-$seriesId") else flowOf(null),
        observeDefaults(memberId),
    ) { own, series, defaults -> EventReminders(own, series, defaults) }

    override suspend fun setForEvent(memberId: String, eventId: String, offsets: Set<ReminderOffset>) {
        reminders(memberId).document(eventId)
            .set(mapOf("eventId" to eventId, "offsets" to keys(offsets), "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun setForSeries(memberId: String, seriesId: String, eventId: String, offsets: Set<ReminderOffset>) {
        val batch = db.batch()
        batch.set(
            reminders(memberId).document("series-$seriesId"),
            mapOf("seriesId" to seriesId, "offsets" to keys(offsets), "updatedAt" to FieldValue.serverTimestamp()),
        )
        // « Toutes les fois » covers this one too: its own choice goes.
        batch.delete(reminders(memberId).document(eventId))
        batch.commit().await()
    }

    override suspend fun setDefaults(memberId: String, offsets: Set<ReminderOffset>) {
        settings(memberId).set(mapOf("defaultReminders" to keys(offsets)), SetOptions.merge()).await()
    }

    private fun keys(offsets: Set<ReminderOffset>) = ReminderOffset.entries.filter { it in offsets }.map { it.key }
}
