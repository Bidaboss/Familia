package fr.familia.app.core.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** How long the check after sign-in waits for the writes this phone still holds. */
private const val PENDING_WAIT_MILLIS = 60_000L

/**
 * A creation noted in [SendJournal] while it starts, and forgotten once Firestore answers in this
 * launch. Without a journal (tests, no Firebase), just the write.
 */
suspend fun SendJournal?.track(entry: SendEntry, write: () -> Task<Void>) {
    if (this == null) {
        write().await()
        return
    }
    started(entry)
    val task = write()
    task.addOnCompleteListener { settled(entry.path) }
    task.await()
}

/**
 * Which of [paths] the server does not have, once the writes this phone still holds have had their
 * answer. A document the rules no longer let read is gone too. Null when it cannot tell (no network).
 */
suspend fun missingOnServer(db: FirebaseFirestore, paths: List<String>): Set<String>? = try {
    if (withTimeoutOrNull(PENDING_WAIT_MILLIS) { db.waitForPendingWrites().await() } == null) {
        null
    } else {
        val missing = mutableSetOf<String>()
        var known = true
        for (path in paths) {
            try {
                if (!db.document(path).get(Source.SERVER).await().exists()) missing += path
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.NOT_FOUND -> missing += path
                    else -> known = false
                }
            }
        }
        missing.takeIf { known }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
