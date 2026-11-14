package fr.familia.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreAppConfigRepository(private val db: FirebaseFirestore) : AppConfigRepository {

    override fun observeAppConfig(): Flow<AppConfig> = callbackFlow {
        val registration = db.collection("config").document("app").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    AppConfig(
                        latestVersionCode = snapshot?.getLong("latestVersionCode"),
                        latestVersionName = snapshot?.getString("latestVersionName"),
                        downloadUrl = snapshot?.getString("downloadUrl"),
                    ),
                )
            }
        }
        awaitClose { registration.remove() }
    }
}
