package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Phones in `members/{id}/devices/{token}`, settings in `members/{id}/settings/notifications`. */
class FirestoreNotificationRepository(
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : NotificationRepository {

    override suspend fun registerThisDevice(memberId: String) {
        val token = messaging.token.await()
        db.collection("members").document(memberId).collection("devices").document(token)
            .set(mapOf("updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override fun observeDayBeforeReminder(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                // No document yet: the reminder is on.
                trySend(snapshot?.getBoolean("dayBeforeReminder") != false)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean) {
        // Merge: the same document holds the other settings (goodNews, récit 32).
        settings(memberId).set(mapOf("dayBeforeReminder" to enabled), SetOptions.merge()).await()
    }

    override fun observeGoodNews(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getBoolean("goodNews") != false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setGoodNews(memberId: String, enabled: Boolean) {
        settings(memberId).set(mapOf("goodNews" to enabled), SetOptions.merge()).await()
    }

    override fun observeWeekly(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getBoolean("weekly") != false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setWeekly(memberId: String, enabled: Boolean) {
        settings(memberId).set(mapOf("weekly" to enabled), SetOptions.merge()).await()
    }

    override fun observeSouvenirs(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getBoolean("souvenirs") != false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setSouvenirs(memberId: String, enabled: Boolean) {
        settings(memberId).set(mapOf("souvenirs" to enabled), SetOptions.merge()).await()
    }

    override fun observeTree(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getBoolean("tree") != false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setTree(memberId: String, enabled: Boolean) {
        settings(memberId).set(mapOf("tree" to enabled), SetOptions.merge()).await()
    }

    override fun observeRecap(memberId: String): Flow<Boolean> = callbackFlow {
        val registration = settings(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getBoolean("recap") != false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setRecap(memberId: String, enabled: Boolean) {
        settings(memberId).set(mapOf("recap" to enabled), SetOptions.merge()).await()
    }

    private fun settings(memberId: String) =
        db.collection("members").document(memberId).collection("settings").document("notifications")
}
