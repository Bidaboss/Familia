package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.KinLinkKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * kin/{kinId}, kinLinks/{linkId} and kinWords/{memberId}. Every member reads the tree; the rules
 * let the admin principal and the chefs of the household change it. See docs/data-model.md.
 */
class FirestoreGenealogyRepository(private val db: FirebaseFirestore) : GenealogyRepository {

    override fun observeKin(): Flow<List<Kin>> = observe(db.collection(KIN)) { it.toKin() }

    override fun observeLinks(): Flow<List<KinLink>> = observe(db.collection(LINKS)) { doc ->
        val kind = KinLinkKind.of(doc.getString("kind")) ?: return@observe null
        val a = doc.getString("aId") ?: return@observe null
        val b = doc.getString("bId") ?: return@observe null
        KinLink(kind, a, b)
    }

    override fun observeWords(memberId: String): Flow<Map<String, String>> = callbackFlow {
        val registration = db.collection(WORDS).document(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                val words = (snapshot?.get("words") as? Map<*, *>).orEmpty()
                trySend(words.entries.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newKinId(): String = db.collection(KIN).document().id

    override suspend fun saveKin(kin: Kin, byMemberId: String, firstLink: KinLink?) {
        val fields = buildMap<String, Any> {
            put("firstName", kin.firstName.trim().take(Kin.MAX_NAME))
            kin.lastName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("lastName", it.take(Kin.MAX_NAME)) }
            put("living", kin.living)
            kin.born?.takeIf(Kin::isDate)?.let { put("born", it) }
            if (!kin.living) kin.died?.takeIf(Kin::isDate)?.let { put("died", it) }
            kin.note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it.take(Kin.MAX_NOTE)) }
            put("householdId", kin.householdId)
            put("updatedBy", byMemberId)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        val batch = db.batch()
        batch.set(db.collection(KIN).document(kin.id), fields)
        firstLink?.let { batch.set(db.collection(LINKS).document(it.id), linkFields(it, byMemberId)) }
        batch.commit().await()
    }

    override suspend fun deleteKin(id: String) {
        db.collection(KIN).document(id).delete().await()
    }

    override suspend fun addLink(link: KinLink, byMemberId: String) {
        db.collection(LINKS).document(link.id).set(linkFields(link, byMemberId)).await()
    }

    override suspend fun removeLink(link: KinLink) {
        db.collection(LINKS).document(link.id).delete().await()
    }

    override suspend fun saveWords(memberId: String, words: Map<String, String>) {
        db.collection(WORDS).document(memberId)
            .set(mapOf("words" to words, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    private fun linkFields(link: KinLink, byMemberId: String) = mapOf(
        "kind" to link.kind.key,
        "aId" to link.aId,
        "bId" to link.bId,
        "updatedBy" to byMemberId,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun <T : Any> observe(query: Query, read: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull(read))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toKin(): Kin? {
        val firstName = getString("firstName") ?: return null
        return Kin(
            id = id,
            firstName = firstName,
            lastName = getString("lastName"),
            living = getBoolean("living") ?: false,
            born = getString("born"),
            died = getString("died"),
            note = getString("note"),
            householdId = getString("householdId").orEmpty(),
        )
    }

    private companion object {
        const val KIN = "kin"
        const val LINKS = "kinLinks"
        const val WORDS = "kinWords"
    }
}
