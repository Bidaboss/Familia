package fr.familia.app.core.album

import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A photo of an album: the photos of a trip (récit 15), and later of an event (récit 41). The
 * album lives in [folder], for example `trips/{tripId}`: its records are in Firestore at
 * `{folder}/photos/{authorId}_{slot}` and its files in Storage at [photoPath] and [thumbnailPath].
 * Each member has [MAX_PER_MEMBER] numbered places in an album; [slot] is one of them, so the
 * security rules cap the photos per person without counting them.
 */
data class AlbumPhoto(
    val folder: String,
    val authorId: String,
    /** Kept so the photo stays signed once its author has left. */
    val authorName: String,
    val slot: Int,
    /** Null just after this phone sent it, until the server time arrives. */
    val createdAt: Instant?,
) {
    val id: String get() = photoId(authorId, slot)
    val photoPath: String get() = "$folder/$authorId/$slot.jpg"
    val thumbnailPath: String get() = "$folder/$authorId/$slot-thumb.jpg"

    companion object {
        /** As the security rules allow (firestore.rules and storage.rules). */
        const val MAX_PER_MEMBER = 30

        fun photoId(authorId: String, slot: Int): String = "${authorId}_$slot"

        /** The free places of a member, smallest first, given the places already [taken], out of [max]. */
        fun freeSlots(taken: Collection<Int>, max: Int = MAX_PER_MEMBER): List<Int> = (1..max).filterNot { it in taken }
    }
}

/** Newest first; a photo just sent from this phone, with no server time yet, comes first. */
val albumPhotoOrder: Comparator<AlbumPhoto> =
    compareByDescending<AlbumPhoto> { it.createdAt ?: Instant.MAX }.thenBy { it.id }

/** The photo records of the albums. The files are sent and removed through PhotoStorage. */
interface AlbumPhotoRepository {
    /** The photos of the album in [folder], in [albumPhotoOrder], updated live. */
    fun observePhotos(folder: String): Flow<List<AlbumPhoto>>

    /** Records a photo whose files are already in Storage, signed by [me]. */
    suspend fun addPhoto(folder: String, slot: Int, me: Member)

    /** Removes the record of a photo. The caller removes its two files from Storage first. */
    suspend fun deletePhoto(photo: AlbumPhoto)
}
