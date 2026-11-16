package fr.familia.app.feature.goodnews.edit

import fr.familia.app.core.model.GoodNews

/** What the « Publier une Bonne nouvelle » form holds. [photos] are the content URIs chosen. */
data class GoodNewsForm(
    val text: String = "",
    val photos: List<String> = emptyList(),
    val forMemberId: String,
    val mentionIds: Set<String> = emptySet(),
    /** Counts for the challenge of a foyer of the month (récit 35 bis); the photo is already required. */
    val forChallenge: Boolean = false,
    /** « Les petits » (récit 43): only when it is for a child, not for oneself. */
    val littleOnes: Boolean = false,
)

enum class GoodNewsFormError { TextMissing, TextTooLong, NoPhoto }

/** A text and one to three photos (récit 32). When editing, the photos stay as they are. */
fun GoodNewsForm.validate(isNew: Boolean): Set<GoodNewsFormError> = buildSet {
    if (text.isBlank()) add(GoodNewsFormError.TextMissing)
    if (text.length > GoodNews.TEXT_MAX) add(GoodNewsFormError.TextTooLong)
    if (isNew && photos.isEmpty()) add(GoodNewsFormError.NoPhoto)
}

/** Adds the photos just picked, never more than three in all. */
fun GoodNewsForm.withPhotos(picked: List<String>): GoodNewsForm =
    copy(photos = (photos + picked).distinct().take(GoodNews.PHOTOS_MAX))
