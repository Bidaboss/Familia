package fr.familia.app.core.photo

import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * The one photo of an element (PRD, récit 44): the cover of an event, later the photo of a wish.
 * In Storage it is two files side by side, [photoPath] (1600 px, 500 KB at most) and [thumbPath]
 * (400 px); the document keeps only their common base, without « .jpg ». A new photo always gets
 * a new name, since Storage never replaces a file.
 */
object SinglePhoto {
    fun photoPath(base: String) = "$base.jpg"

    fun thumbPath(base: String) = "$base-thumb.jpg"

    /** 20 letters and digits, the shape firebase/storage.rules accepts. */
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(20)

    /** events/{eventId}/cover/{memberId}/{photoId}: the server removes it with the event. */
    fun eventCover(eventId: String, memberId: String, photoId: String = newId()) =
        "events/$eventId/cover/$memberId/$photoId"
}

/** What the member did with the photo on the edit screen. */
sealed interface PhotoEdit {
    data object Unchanged : PhotoEdit

    /** A photo chosen in the gallery or taken with the camera, as a content URI. */
    data class Picked(val source: String) : PhotoEdit

    data object Removed : PhotoEdit
}

/**
 * Saves the photo change of one element, in two steps around the write of its document:
 * [prepare] sends the new photo first, since the rules of the document only accept a photo already
 * sent; [cleanUp] then removes the photo the document no longer names.
 */
class SinglePhotoSaver(private val uploader: PhotoUploader, private val storage: PhotoStorage) {

    /**
     * The base to write in the document, given the [current] one. Throws when the new photo cannot
     * be sent: the document must not be written then.
     */
    suspend fun prepare(current: String?, edit: PhotoEdit, newBase: () -> String): String? = when (edit) {
        PhotoEdit.Unchanged -> current
        PhotoEdit.Removed -> null
        is PhotoEdit.Picked -> newBase().also { uploader.upload(edit.source, SinglePhoto.photoPath(it), SinglePhoto.thumbPath(it)) }
    }

    /**
     * Once the document is written with [saved]: removes [previous] if it is no longer named. Also
     * call it with the prepared base and null when the document could not be written. Never fails:
     * a file left behind only takes space.
     */
    suspend fun cleanUp(previous: String?, saved: String?) {
        if (previous == null || previous == saved) return
        for (path in listOf(SinglePhoto.photoPath(previous), SinglePhoto.thumbPath(previous))) {
            try {
                storage.delete(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Already gone, or offline: nothing the member can do about it.
            }
        }
    }
}
