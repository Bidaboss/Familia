package fr.familia.app.core.data

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The film of a day of a step, published for the followers of the trip (récit 108, règle 32,
 * PRD 156 and 157): made once on the phone of a participant, sent to Storage, seen by all.
 */
data class TripFilm(
    val id: String,
    val authorId: String,
    val authorName: String,
    val stepId: String,
    val day: LocalDate,
    val seconds: Int,
    val photoCount: Int,
    val createdAt: Instant?,
)

/** How a publication ended. */
enum class FilmPublication {
    Published,

    /** Someone published the film of this day first: theirs stays, ours is not sent (C2 of #559). */
    AlreadyThere,
}

object TripFilms {
    /** As the rules allow (firestore.rules and storage.rules). */
    const val MAX_BYTES = 10L * 1024 * 1024
    const val MAX_SECONDS = 300
    const val MAX_PHOTOS = 300
    const val CONTENT_TYPE = "video/mp4"

    /** One film a step and a day: {stepId}_{yyyymmdd}. */
    fun id(stepId: String, day: LocalDate): String = "${stepId}_${day.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    /** Where the file of a film is in Storage. */
    fun path(tripId: String, authorId: String, filmId: String): String = "trips/$tripId/films/$authorId/$filmId.mp4"
}

interface TripFilmRepository {
    /** The published films of the trip, updated live; none when they cannot be read. */
    fun observe(tripId: String): Flow<List<TripFilm>>

    /** Sends the file of the film of [day] of [stepId], then announces it; nothing is left behind if it is refused. */
    suspend fun publish(tripId: String, me: Member, stepId: String, day: LocalDate, file: File, seconds: Int, photoCount: Int): FilmPublication

    /** Where the phone plays it from; null when it is gone. */
    suspend fun playUrl(tripId: String, film: TripFilm): String?
}

class FirestoreTripFilmRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : TripFilmRepository {

    private fun films(tripId: String) = db.collection("trips").document(tripId).collection("films")

    override fun observe(tripId: String): Flow<List<TripFilm>> = callbackFlow {
        val registration = films(tripId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    trySend(emptyList())
                    close()
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty().mapNotNull { doc ->
                    TripFilm(
                        id = doc.id,
                        authorId = doc.getString("authorId") ?: return@mapNotNull null,
                        authorName = doc.getString("authorName").orEmpty(),
                        stepId = doc.getString("stepId") ?: return@mapNotNull null,
                        day = doc.getString("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null,
                        seconds = doc.getLong("seconds")?.toInt() ?: return@mapNotNull null,
                        photoCount = doc.getLong("photoCount")?.toInt() ?: return@mapNotNull null,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.toInstant(),
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun publish(tripId: String, me: Member, stepId: String, day: LocalDate, file: File, seconds: Int, photoCount: Int): FilmPublication {
        require(file.length() in 1..TripFilms.MAX_BYTES && seconds in 1..TripFilms.MAX_SECONDS && photoCount in 2..TripFilms.MAX_PHOTOS)
        val filmId = TripFilms.id(stepId, day)
        val ref = films(tripId).document(filmId)
        // Someone was first: nothing is sent (C2 of #559).
        if (ref.get().await().exists()) return FilmPublication.AlreadyThere
        val fileRef = storage.reference.child(TripFilms.path(tripId, me.id, filmId))
        // A file of ours left by an earlier try cut off before its record: Storage refuses to replace it.
        deleteQuietly(fileRef)
        val metadata = StorageMetadata.Builder().setContentType(TripFilms.CONTENT_TYPE).build()
        fileRef.putFile(Uri.fromFile(file), metadata).await()
        try {
            ref.set(
                mapOf(
                    "authorId" to me.id,
                    "authorName" to me.firstName,
                    "stepId" to stepId,
                    "day" to day.toString(),
                    "seconds" to seconds.toLong(),
                    "photoCount" to photoCount.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: Exception) {
            // Refused (someone published at the same time) or cut off: our file does not stay alone.
            withContext(NonCancellable) { deleteQuietly(fileRef) }
            if (e is CancellationException) throw e
            if (runCatching { ref.get().await().exists() }.getOrDefault(false)) return FilmPublication.AlreadyThere
            throw e
        }
        return FilmPublication.Published
    }

    override suspend fun playUrl(tripId: String, film: TripFilm): String? = try {
        storage.reference.child(TripFilms.path(tripId, film.authorId, film.id)).downloadUrl.await().toString()
    } catch (e: StorageException) {
        null
    }

    private suspend fun deleteQuietly(fileRef: StorageReference) {
        try {
            fileRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not there, or no network: the next try says so.
        }
    }
}
