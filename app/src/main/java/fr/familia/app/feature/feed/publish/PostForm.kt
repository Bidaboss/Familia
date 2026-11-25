package fr.familia.app.feature.feed.publish

import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.PickGroup

/** What « Publier dans la gazette » holds. [photos] are the content URIs chosen. */
data class PostForm(
    val text: String = "",
    val photos: List<String> = emptyList(),
    val mentionIds: Set<String> = emptySet(),
)

enum class PostFormError { Empty, TextTooLong }

/** A word, a photo, or both (brief §5: « Publier » is on as soon as there is one). Editing keeps the photos. */
fun PostForm.validate(hasPhotos: Boolean = photos.isNotEmpty()): Set<PostFormError> = buildSet {
    if (text.isBlank() && !hasPhotos) add(PostFormError.Empty)
    if (text.length > GazettePost.TEXT_MAX) add(PostFormError.TextTooLong)
}

/** Adds the photos just picked, never more than six in all (PRD 119 b). */
fun PostForm.withPhotos(picked: List<String>): PostForm =
    copy(photos = (photos + picked).distinct().take(GazettePost.PHOTOS_MAX))

enum class PostFailure { PhotoUnreadable, Failed }

sealed interface PostEditUiState {
    data object Loading : PostEditUiState

    /** Removed meanwhile, or not the author's. */
    data object Unavailable : PostEditUiState

    data class Editing(
        val form: PostForm,
        /** Null for a new publication; the one whose text changes otherwise (its photos stay). */
        val existing: GazettePost?,
        val me: Member,
        /** Who can be named: the others with the app (PRD 119 c). */
        val candidates: List<Member>,
        val mentionGroups: List<PickGroup> = emptyList(),
        val errors: Set<PostFormError> = emptySet(),
        /** While sending: the photo being sent (1 to 6), or 0 once the photos are sent. */
        val sendingPhoto: Int? = null,
        val progress: Float? = null,
        val offline: Boolean = false,
        val failure: PostFailure? = null,
    ) : PostEditUiState {
        val isNew: Boolean get() = existing == null
        val isSaving: Boolean get() = sendingPhoto != null
        val canAddPhotos: Boolean get() = isNew && form.photos.size < GazettePost.PHOTOS_MAX
        val canPublish: Boolean get() = form.validate(form.photos.isNotEmpty() || existing?.photoIds?.isNotEmpty() == true).isEmpty()
    }

    /** Published (or queued): the screen closes. */
    data object Done : PostEditUiState
}
