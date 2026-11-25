package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class FirestoreSurpriseRepository(private val db: FirebaseFirestore) : SurpriseRepository {

    private val surprises get() = db.collection("surprises")
    private val open get() = db.collection("openSurprises")

    override fun observeMine(memberId: String): Flow<List<Surprise>> = callbackFlow {
        val registration = surprises.whereArrayContains("memberIds", memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toSurprise() }.sortedBy { it.date })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeSurprise(id: String): Flow<Surprise?> = callbackFlow {
        val registration = surprises.document(id).addSnapshotListener { snapshot, error ->
            when {
                // Not a member any more: the rules refuse the read, the surprise is simply gone for them.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() }?.toSurprise())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeOpenFor(beneficiaryId: String): Flow<List<OpenSurprise>> = callbackFlow {
        val registration = open.whereEqualTo("beneficiaryId", beneficiaryId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        OpenSurprise(
                            id = doc.id,
                            beneficiaryId = doc.getString("beneficiaryId") ?: return@mapNotNull null,
                            title = doc.getString("title").orEmpty(),
                            creatorName = doc.getString("creatorName").orEmpty(),
                        )
                    },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newSurpriseId(): String = surprises.document().id

    override suspend fun saveSurprise(surprise: Surprise, isNew: Boolean) {
        val fields = mutableMapOf<String, Any>(
            "beneficiaryId" to surprise.beneficiaryId,
            "title" to surprise.title,
            "occasion" to surprise.occasion.firestoreValue,
            "date" to surprise.date.toString(),
            "memberIds" to surprise.memberIds,
            "creatorId" to surprise.creatorId,
            "creatorName" to surprise.creatorName,
            "status" to "preparing",
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val batch = db.batch()
        if (isNew) {
            fields["createdAt"] = FieldValue.serverTimestamp()
            batch.set(surprises.document(surprise.id), fields)
        } else {
            batch.update(surprises.document(surprise.id), fields)
        }
        batch.set(
            open.document(surprise.id),
            mapOf(
                "beneficiaryId" to surprise.beneficiaryId,
                "title" to surprise.title,
                "creatorName" to surprise.creatorName,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
    }

    override suspend fun deleteSurprise(id: String) {
        db.batch().delete(surprises.document(id)).delete(open.document(id)).commit().await()
    }

    override suspend fun joinSurprise(id: String, memberId: String) {
        surprises.document(id).update(
            mapOf("memberIds" to FieldValue.arrayUnion(memberId), "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    private fun proposals(surpriseId: String) = surprises.document(surpriseId).collection("proposals")

    override fun observeProposals(surpriseId: String): Flow<List<SurpriseProposal>> = callbackFlow {
        val registration = proposals(surpriseId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.documents.orEmpty().map { it.toProposal() })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeMyRatings(surpriseId: String, memberId: String): Flow<Map<String, Int>> = callbackFlow {
        // A collection group would need an index; the notes of one surprise are few, read one by one.
        val registrations = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        val notes = mutableMapOf<String, Int>()
        val list = proposals(surpriseId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val ids = snapshot?.documents.orEmpty().map { it.id }.toSet()
            (registrations.keys - ids).forEach { gone ->
                registrations.remove(gone)?.remove()
                notes.remove(gone)
            }
            (ids - registrations.keys).forEach { id ->
                registrations[id] = proposals(surpriseId).document(id).collection("ratings").document(memberId)
                    .addSnapshotListener { note, noteError ->
                        if (noteError == null) {
                            val stars = note?.getLong("stars")?.toInt()
                            if (stars != null) notes[id] = stars else notes.remove(id)
                            trySend(notes.toMap())
                        }
                    }
            }
            trySend(notes.toMap())
        }
        awaitClose {
            list.remove()
            registrations.values.forEach { it.remove() }
        }
    }

    override suspend fun rate(surpriseId: String, proposal: SurpriseProposal, memberId: String, stars: Int?) {
        val doc = proposals(surpriseId).document(proposal.id)
        val note = doc.collection("ratings").document(memberId)
        if (stars == null) {
            note.delete().await()
            return
        }
        val batch = db.batch()
        if (proposal.kind == ProposalKind.Wish) {
            // The first note of a wish writes its proposal; the server keeps the sum and the count.
            batch.set(doc, proposalFields(proposal), SetOptions.mergeFields(PROPOSAL_FIELDS))
        }
        batch.set(note, mapOf("stars" to stars.coerceIn(1, 5), "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

    override suspend fun addIdea(surpriseId: String, proposal: SurpriseProposal) {
        proposals(surpriseId).document(proposal.id).set(proposalFields(proposal)).await()
    }

    override suspend fun removeIdea(surpriseId: String, proposalId: String) {
        proposals(surpriseId).document(proposalId).delete().await()
    }

    override fun observeHistory(beneficiaryId: String): Flow<List<GiftHistoryItem>> = callbackFlow {
        val registration = db.collection("giftHistory").document(beneficiaryId).collection("items").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    snapshot?.documents.orEmpty().map { doc ->
                        GiftHistoryItem(
                            id = doc.id,
                            title = doc.getString("title").orEmpty(),
                            year = doc.getLong("year")?.toInt() ?: 0,
                            occasion = SurpriseOccasion.fromFirestore(doc.getString("occasion")),
                        )
                    }.sortedByDescending { it.year },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setRemoved(surpriseId: String, proposal: SurpriseProposal, removed: Boolean) {
        val doc = proposals(surpriseId).document(proposal.id)
        // A wish never noted has no proposal yet: written first, as at its first note.
        if (proposal.kind == ProposalKind.Wish) doc.set(proposalFields(proposal), SetOptions.mergeFields(PROPOSAL_FIELDS)).await()
        doc.update("removed", removed).await()
    }

    private fun takes(surpriseId: String) = surprises.document(surpriseId).collection("takes")

    override fun observeTakes(surpriseId: String): Flow<List<SurpriseTake>> = callbackFlow {
        val registration = takes(surpriseId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    snapshot?.documents.orEmpty().map { doc ->
                        SurpriseTake(
                            proposalId = doc.id,
                            kind = TakeKind.fromFirestore(doc.getString("kind")),
                            ownerId = doc.getString("ownerId").orEmpty(),
                            boughtBy = doc.getString("boughtBy"),
                            goal = doc.getLong("goal")?.toInt(),
                            sharesTotal = doc.getLong("sharesTotal")?.toInt() ?: 0,
                            sharesCount = doc.getLong("sharesCount")?.toInt() ?: 0,
                        )
                    },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun take(surpriseId: String, proposal: SurpriseProposal, memberId: String, kind: TakeKind, goal: Int?) {
        val batch = db.batch()
        if (proposal.kind == ProposalKind.Wish) {
            batch.set(proposals(surpriseId).document(proposal.id), proposalFields(proposal), SetOptions.mergeFields(PROPOSAL_FIELDS))
        }
        batch.set(
            takes(surpriseId).document(proposal.id),
            mapOf(
                "kind" to kind.firestoreValue,
                "ownerId" to memberId,
                "boughtBy" to null,
                "goal" to if (kind == TakeKind.Shared) goal?.coerceIn(1, SurpriseTake.MAX_AMOUNT) else null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
    }

    override suspend fun release(surpriseId: String, proposalId: String) {
        takes(surpriseId).document(proposalId).delete().await()
    }

    override fun observeMyShares(surpriseId: String, memberId: String): Flow<Map<String, Int>> =
        perChild(takes(surpriseId), { it.getString("kind") == TakeKind.Shared.firestoreValue }) { id ->
            takes(surpriseId).document(id).collection("shares").document(memberId)
        }

    override fun observeShares(surpriseId: String, proposalId: String): Flow<Map<String, Int>> = callbackFlow {
        val registration = takes(surpriseId).document(proposalId).collection("shares").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.documents.orEmpty().mapNotNull { doc -> doc.getLong("amount")?.toInt()?.let { doc.id to it } }.toMap())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setShare(surpriseId: String, proposalId: String, memberId: String, amount: Int?) {
        val doc = takes(surpriseId).document(proposalId).collection("shares").document(memberId)
        if (amount == null) {
            doc.delete().await()
        } else {
            doc.set(mapOf("amount" to amount.coerceIn(1, SurpriseTake.MAX_AMOUNT), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override fun observeMemberRows(surpriseId: String): Flow<List<SurpriseMemberRow>> = callbackFlow {
        val registration = surprises.document(surpriseId).collection("members").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    snapshot?.documents.orEmpty().map { doc ->
                        SurpriseMemberRow(
                            memberId = doc.id,
                            validated = doc.getBoolean("validated") == true,
                            declined = doc.getBoolean("declined") == true,
                            muted = doc.getBoolean("muted") == true,
                        )
                    },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setValidated(surpriseId: String, memberId: String, validated: Boolean) {
        surprises.document(surpriseId).collection("members").document(memberId)
            .set(mapOf("validated" to validated, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    override suspend fun decline(surpriseId: String, memberId: String) {
        surprises.document(surpriseId).collection("members").document(memberId)
            .set(mapOf("declined" to true, "validated" to false, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    override suspend fun markOffered(surpriseId: String, memberId: String) {
        surprises.document(surpriseId).update(
            mapOf(
                "status" to "offered",
                "offeredAt" to FieldValue.serverTimestamp(),
                "offeredBy" to memberId,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /**
     * One number per document of [parent] that passes [keep], read from the child [childOf] gives
     * (« amount »): my parts, one listener each, as the notes. A refused read leaves it out.
     */
    private fun perChild(
        parent: CollectionReference,
        keep: (DocumentSnapshot) -> Boolean,
        childOf: (String) -> com.google.firebase.firestore.DocumentReference,
    ): Flow<Map<String, Int>> = callbackFlow {
        val registrations = mutableMapOf<String, ListenerRegistration>()
        val values = mutableMapOf<String, Int>()
        val list = parent.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val ids = snapshot?.documents.orEmpty().filter(keep).map { it.id }.toSet()
            (registrations.keys - ids).forEach { gone ->
                registrations.remove(gone)?.remove()
                values.remove(gone)
            }
            (ids - registrations.keys).forEach { id ->
                registrations[id] = childOf(id).addSnapshotListener { child, childError ->
                    if (childError == null) {
                        val amount = child?.getLong("amount")?.toInt()
                        if (amount != null) values[id] = amount else values.remove(id)
                        trySend(values.toMap())
                    }
                }
            }
            trySend(values.toMap())
        }
        awaitClose {
            list.remove()
            registrations.values.forEach { it.remove() }
        }
    }

    private fun proposalFields(p: SurpriseProposal): Map<String, Any?> = buildMap {
        put("kind", p.kind.firestoreValue)
        put("title", p.title)
        put("price", p.price)
        put("url", p.url)
        put("authorId", p.authorId)
        put("authorName", p.authorName)
        put("removed", false)
        put("createdAt", FieldValue.serverTimestamp())
        when (p.kind) {
            ProposalKind.Wish -> put("wishItemId", p.id.removePrefix("wish-"))
            ProposalKind.Idea -> put("notebookEntryId", p.id.removePrefix("idea-"))
        }
    }

    private fun DocumentSnapshot.toProposal(): SurpriseProposal = SurpriseProposal(
        id = id,
        kind = ProposalKind.fromFirestore(getString("kind")),
        title = getString("title").orEmpty(),
        price = getDouble("price"),
        url = getString("url"),
        authorId = getString("authorId").orEmpty(),
        authorName = getString("authorName").orEmpty(),
        removed = getBoolean("removed") == true,
        ratingSum = getLong("ratingSum")?.toInt() ?: 0,
        ratingCount = getLong("ratingCount")?.toInt() ?: 0,
    )

    private fun DocumentSnapshot.toSurprise(): Surprise? {
        if (getString("status") != "preparing") return null
        return Surprise(
            id = id,
            beneficiaryId = getString("beneficiaryId") ?: return null,
            title = getString("title").orEmpty(),
            occasion = SurpriseOccasion.fromFirestore(getString("occasion")),
            date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            memberIds = (get("memberIds") as? List<*>).orEmpty().filterIsInstance<String>(),
            creatorId = getString("creatorId").orEmpty(),
            creatorName = getString("creatorName").orEmpty(),
        )
    }
}

/** The fields the first note of a wish writes, never the sum and the count kept by the server. */
private val PROPOSAL_FIELDS = listOf("kind", "title", "price", "url", "authorId", "authorName", "wishItemId")
