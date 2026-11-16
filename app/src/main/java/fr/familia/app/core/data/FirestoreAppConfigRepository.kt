package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import fr.familia.app.core.whatsnew.WhatsNew
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAppConfigRepository(private val db: FirebaseFirestore) : AppConfigRepository {

    override fun observeAppConfig(): Flow<AppConfig> = callbackFlow {
        val registration = db.collection("config").document("app").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(toConfig(snapshot))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchAppConfig(): AppConfig = try {
        toConfig(db.collection("config").document("app").get(Source.SERVER).await())
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw AppConfigOfflineException(e) else throw e
    }

    override suspend fun setFeature(key: String, enabled: Boolean) {
        db.collection("config").document("app")
            .set(mapOf("features" to mapOf(key to enabled)), SetOptions.merge())
            .await()
    }

    private fun toConfig(snapshot: DocumentSnapshot?) = AppConfig(
        latestVersionCode = snapshot?.getLong("latestVersionCode"),
        latestVersionName = snapshot?.getString("latestVersionName"),
        apkFileId = snapshot?.getString("apkFileId"),
        apkSize = snapshot?.getLong("apkSize"),
        apkSha256 = snapshot?.getString("apkSha256"),
        features = (snapshot?.get("features") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Boolean)?.let { key to it } } }
            .toMap(),
        whatsNew = WhatsNew.parse(snapshot?.get("whatsNew")),
    )
}
