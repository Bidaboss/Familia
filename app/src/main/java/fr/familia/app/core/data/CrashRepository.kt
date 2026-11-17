package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.crash.PendingCrash
import fr.familia.app.core.model.CrashReport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** crashReports/{reportId} (PRD, récit 6 sexies, rév. 220). */
interface CrashRepository {
    /** Sends a crash kept on the phone, in the name of [memberId]; its id makes a second send refused. */
    suspend fun send(memberId: String, crash: PendingCrash)

    /** Every report, for the admin principal (the rules refuse anyone else). */
    fun observeAll(): Flow<List<CrashReport>>

    /** « Marquer comme réglé »: the reports of this kind go; if it comes back, it is new again. */
    suspend fun settle(signature: String)
}

class FirestoreCrashRepository(private val db: FirebaseFirestore) : CrashRepository {

    private val all = db.collection("crashReports")

    override suspend fun send(memberId: String, crash: PendingCrash) {
        all.document(crash.id).set(
            mapOf(
                "memberId" to memberId,
                "appVersion" to crash.appVersion.take(40),
                "versionCode" to crash.versionCode,
                "android" to crash.android.take(20),
                "device" to crash.device.take(80),
                "screen" to crash.screen.take(80),
                "trace" to crash.trace.take(CrashReport.TRACE_MAX),
                "signature" to crash.signature,
                "crashedAt" to Timestamp(Instant.ofEpochMilli(crash.crashedAtMillis)),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override fun observeAll(): Flow<List<CrashReport>> = callbackFlow {
        val registration = all.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.documents.orEmpty().mapNotNull(::toReport))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun settle(signature: String) {
        val same = all.whereEqualTo("signature", signature).get().await()
        // A family has few: one batch (500 writes at most) is plenty.
        val batch = db.batch()
        same.documents.take(450).forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    private fun toReport(doc: DocumentSnapshot): CrashReport? {
        val signature = doc.getString("signature") ?: return null
        return CrashReport(
            id = doc.id,
            memberId = doc.getString("memberId").orEmpty(),
            appVersion = doc.getString("appVersion").orEmpty(),
            versionCode = doc.getLong("versionCode") ?: 0,
            android = doc.getString("android").orEmpty(),
            device = doc.getString("device").orEmpty(),
            screen = doc.getString("screen").orEmpty(),
            trace = doc.getString("trace").orEmpty(),
            signature = signature,
            crashedAt = doc.getTimestamp("crashedAt")?.toInstant() ?: Instant.EPOCH,
        )
    }
}
