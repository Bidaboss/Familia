package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Base64

/**
 * calendarFeeds/{memberId} (PRD, récit 49): the secret token of an adult's calendar link. The
 * function calendarFeed serves the planning as .ics to whoever holds it; a new token cuts the old link.
 */
interface CalendarFeedRepository {
    /** My token, or null while I have no link. */
    fun observeToken(memberId: String): Flow<String?>

    /** Writes a fresh token: the old link stops working at once. */
    suspend fun renew(memberId: String)

    /** « Supprimer mon lien »: the link stops working, and none is made in its place. */
    suspend fun remove(memberId: String)
}

/** The address Google Agenda subscribes to (europe-west1, like every function of Familia). */
const val CALENDAR_FEED_BASE = "https://europe-west1-familia-93286.cloudfunctions.net/calendarFeed"

fun calendarFeedUrl(token: String): String = "$CALENDAR_FEED_BASE?t=$token"

/** 32 random bytes in base64url without padding: 43 characters, as the rules ask. */
fun newCalendarFeedToken(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

class FirestoreCalendarFeedRepository(private val db: FirebaseFirestore) : CalendarFeedRepository {

    private fun ref(memberId: String) = db.collection("calendarFeeds").document(memberId)

    override fun observeToken(memberId: String): Flow<String?> = callbackFlow {
        val registration = ref(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getString("token"))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun renew(memberId: String) {
        ref(memberId).set(mapOf("token" to newCalendarFeedToken(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun remove(memberId: String) {
        ref(memberId).delete().await()
    }
}
