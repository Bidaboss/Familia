package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** members/{memberId}/souvenirs and souvenirCounters/{memberId}. See docs/data-model.md. */
class FirestoreSouvenirRepository(private val db: FirebaseFirestore) : SouvenirRepository {

    private fun souvenirs(memberId: String) = db.collection("members").document(memberId).collection("souvenirs")

    override fun observeMine(memberId: String): Flow<List<Souvenir>> = observe(souvenirs(memberId))

    // Récit 114: the « Réussites » are public, the family reads them all.
    override fun observeShown(memberId: String): Flow<List<Souvenir>> = observe(souvenirs(memberId))

    private fun observe(query: Query): Flow<List<Souvenir>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) trySend(emptyList()) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toSouvenir() })
        }
        awaitClose { registration.remove() }
    }

    override fun observeCounters(memberId: String): Flow<SouvenirCounters> = callbackFlow {
        val registration = db.collection("souvenirCounters").document(memberId).addSnapshotListener { snapshot, _ ->
            trySend(
                SouvenirCounters(
                    reactions = snapshot?.getLong("reactions")?.toInt() ?: 0,
                    helpGiven = snapshot?.getLong("helpGiven")?.toInt() ?: 0,
                    weeklyAnswers = snapshot?.getLong("weeklyAnswers")?.toInt() ?: 0,
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    // Récit 114: nobody chooses any more, the rules refuse the change. The switch leaves the profile with lot E.
    override suspend fun setShown(memberId: String, key: String, shown: Boolean) = Unit

    private fun DocumentSnapshot.toSouvenir(): Souvenir? {
        val key = getString("key") ?: id
        return Souvenir(key = key, earnedAt = getTimestamp("earnedAt")?.toInstant(), shown = getBoolean("shown") == true)
    }
}
