package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import fr.familia.app.core.model.Member
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** What a document of a trip is, [key] as stored in Firestore. */
enum class TripDocumentKind(val key: String) {
    Ticket("ticket"),
    Booking("booking"),
    Paper("paper"),
    ;

    companion object {
        fun of(key: String?): TripDocumentKind? = entries.firstOrNull { it.key == key }
    }
}

/** The three kinds of file a document can be, with the extension of its name in Storage. */
enum class TripDocumentType(val contentType: String, val extension: String) {
    Pdf("application/pdf", "pdf"),
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    ;

    companion object {
        fun of(contentType: String?): TripDocumentType? = entries.firstOrNull { it.contentType == contentType }
    }
}

/** A ticket, a booking or a paper of a trip (récit 105, règle 41). */
data class TripDocument(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val kind: TripDocumentKind,
    val type: TripDocumentType,
    val size: Long,
    val createdAt: Instant?,
    /** Who reads it (DESIGN-105): its owner alone when private, or the travellers they picked too. */
    val sharedWith: List<String> = listOf(ownerId),
) {
    val isPrivate: Boolean get() = sharedWith.none { it != ownerId }

    /** trips/{tripId}/documents/{ownerId}/{id}.{ext} in Storage. */
    fun path(tripId: String): String = TripDocuments.path(tripId, ownerId, id, type)
}

object TripDocuments {
    /** 5 MB, as in the rules. */
    const val MAX_BYTES = 5L * 1024 * 1024
    const val TITLE_MAX = 80

    fun path(tripId: String, ownerId: String, id: String, type: TripDocumentType): String =
        "trips/$tripId/documents/$ownerId/$id.${type.extension}"
}

/**
 * « Documents » of a trip (récit 105, règle 41). Never the followers: a passport may be one. Private
 * by default (DESIGN-105): each one is read by its owner and the travellers they share it with. They
 * live out of the album and go with the trip.
 */
interface TripDocumentRepository {
    /** The documents of the trip shared with [memberId] (theirs included), the newest first, updated live. */
    fun observe(tripId: String, memberId: String): Flow<List<TripDocument>>

    /** Sends the file, then writes the document that names it, read by [sharedWith] (the owner first). */
    suspend fun add(tripId: String, me: Member, title: String, kind: TripDocumentKind, type: TripDocumentType, bytes: ByteArray, sharedWith: List<String>)

    /** « Partager… »: the owner changes who reads [document]. */
    suspend fun share(tripId: String, document: TripDocument, sharedWith: List<String>)

    /** The file, to open it on the phone; null when it is gone or unreadable. */
    suspend fun bytes(tripId: String, document: TripDocument): ByteArray?

    /** Removes the file, then the document. */
    suspend fun delete(tripId: String, document: TripDocument)
}

class FirestoreTripDocumentRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : TripDocumentRepository {

    private fun documents(tripId: String) = db.collection("trips").document(tripId).collection("documents")

    // Only what is shared with the reader: the rules can prove it, and refuse the whole folder. Sorted
    // here rather than with orderBy, which would need an index with array-contains.
    override fun observe(tripId: String, memberId: String): Flow<List<TripDocument>> = callbackFlow {
        val registration = documents(tripId).whereArrayContains("sharedWith", memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val kind = TripDocumentKind.of(doc.getString("kind")) ?: return@mapNotNull null
                    val type = TripDocumentType.of(doc.getString("contentType")) ?: return@mapNotNull null
                    TripDocument(
                        id = doc.id,
                        ownerId = doc.getString("ownerId").orEmpty(),
                        ownerName = doc.getString("ownerName").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        kind = kind,
                        type = type,
                        size = doc.getLong("size") ?: 0L,
                        // A document being written has no time yet: it is the newest.
                        createdAt = doc.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant(),
                        sharedWith = (doc.get("sharedWith") as? List<*>).orEmpty().filterIsInstance<String>(),
                    )
                }.sortedByDescending { it.createdAt ?: Instant.MAX },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(tripId: String, me: Member, title: String, kind: TripDocumentKind, type: TripDocumentType, bytes: ByteArray, sharedWith: List<String>) {
        require(bytes.isNotEmpty() && bytes.size <= TripDocuments.MAX_BYTES)
        require(sharedWith.firstOrNull() == me.id)
        val ref = documents(tripId).document()
        val metadata = StorageMetadata.Builder().setContentType(type.contentType).build()
        storage.reference.child(TripDocuments.path(tripId, me.id, ref.id, type)).putBytes(bytes, metadata).await()
        ref.set(
            mapOf(
                "ownerId" to me.id,
                "ownerName" to me.firstName,
                "title" to title.trim().take(TripDocuments.TITLE_MAX),
                "kind" to kind.key,
                "file" to "${ref.id}.${type.extension}",
                "contentType" to type.contentType,
                "size" to bytes.size.toLong(),
                "sharedWith" to sharedWith.distinct(),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun share(tripId: String, document: TripDocument, sharedWith: List<String>) {
        require(sharedWith.firstOrNull() == document.ownerId)
        documents(tripId).document(document.id).update("sharedWith", sharedWith.distinct()).await()
    }

    override suspend fun bytes(tripId: String, document: TripDocument): ByteArray? = runCatching {
        storage.reference.child(document.path(tripId)).getBytes(TripDocuments.MAX_BYTES).await()
    }.getOrNull()

    override suspend fun delete(tripId: String, document: TripDocument) {
        // The file first: a record left behind shows, a file left behind would not. A file already
        // gone, or one the admin principal may not remove (it goes with the trip), lets the record go.
        try {
            storage.reference.child(document.path(tripId)).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND && e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
        }
        documents(tripId).document(document.id).delete().await()
    }
}
