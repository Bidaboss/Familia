package fr.familia.app.core.model

import fr.familia.app.core.album.AlbumPhoto
import java.time.Instant

/**
 * « Le mot de l'étape » (récit 88): a short word a participant writes about a step, with up to
 * [MAX_PHOTOS] photos of the trip album, shown in the order chosen. The followers put a heart and
 * comment (the comments are in [fr.familia.app.core.data.TripWordRepository.comments]).
 */
data class TripWord(
    val stepId: String,
    val text: String,
    /** Ids of the album photos ([AlbumPhoto.id]), in the order chosen. A removed photo just drops out. */
    val photoIds: List<String>,
    val authorId: String,
    /** Kept so the word stays signed once its author has left. */
    val authorName: String,
    /** The members who put a heart. */
    val heartIds: Set<String> = emptySet(),
    /** Null just after this phone sent it, until the server time arrives. */
    val createdAt: Instant? = null,
) {
    fun hearted(memberId: String): Boolean = memberId in heartIds

    /** The chosen photos still in [album], in the order chosen. */
    fun photosIn(album: List<AlbumPhoto>): List<AlbumPhoto> {
        val byId = album.associateBy { it.id }
        return photoIds.mapNotNull { byId[it] }
    }

    companion object {
        /** As the security rules allow. */
        const val MAX_TEXT = 280
        const val MAX_PHOTOS = 4

        /** What is saved: trimmed, cut at [MAX_TEXT]. Null when nothing is left, so there is no word to save. */
        fun clean(text: String): String? = text.trim().take(MAX_TEXT).trim().ifEmpty { null }

        /**
         * The ids after [id] was touched in the picker: chosen if it was not (while there is room),
         * taken off if it was, the others keeping their order, so the numbers stay 1, 2, 3.
         */
        fun toggled(chosen: List<String>, id: String): List<String> = when {
            id in chosen -> chosen - id
            chosen.size < MAX_PHOTOS -> chosen + id
            else -> chosen
        }
    }
}
