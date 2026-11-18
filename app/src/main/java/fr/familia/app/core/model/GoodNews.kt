package fr.familia.app.core.model

import java.time.Instant

/** The six fixed reactions of a good news (PRD, récit 32), stored by name. */
enum class Reaction(val firestoreValue: String, val emoji: String) {
    Heart("heart", "❤️"),
    Clap("clap", "👏"),
    Party("party", "🎉"),
    Laugh("laugh", "😂"),
    Wow("wow", "😮"),
    ThumbsUp("thumbsUp", "👍"),
    ;

    companion object {
        fun fromFirestore(value: Any?): Reaction? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/**
 * A « Bonne nouvelle »: a short text, one to three photos in Storage and the members named.
 * [forMemberId] is whose news it is: the author, or a child of the author's household.
 */
data class GoodNews(
    val id: String,
    val text: String,
    val photoIds: List<String>,
    val authorId: String,
    val authorName: String,
    val forMemberId: String,
    val householdId: String,
    val mentionIds: List<String> = emptyList(),
    val reactions: Map<String, Reaction> = emptyMap(),
    /** The members named who chose « Masquer pour moi »: they no longer see it, the others do. */
    val hiddenByIds: List<String> = emptyList(),
    /** Null until the server has written it (just published, offline). */
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    /** « Défi réussi », published by Familia when the challenge of that month is achieved (récit 35): no photo, no author. */
    val challengeMonth: String? = null,
    /** Linked to the challenge of a foyer for that month (récit 35 bis): it moves its gauge. */
    val forChallenge: String? = null,
    /** « Joyeux anniversaire », published by Familia on the birthday of this member (récit 36): no photo, no author. */
    val birthdayOf: String? = null,
    /** « Les petits » (récit 43): a young child's drawing or success, published for them by a responsible adult. */
    val littleOnes: Boolean = false,
    /** Published on this phone without network: it goes by itself, once, when the network is back. */
    val pending: Boolean = false,
    /** Its comments (récit 67), counted by the server. */
    val commentCount: Int = 0,
) {
    /** Changed by its author after it was published. */
    val isEdited: Boolean get() = createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt.plusSeconds(60))

    /** The member it is for, or a member named: they may hide it for themselves. */
    fun isAbout(memberId: String): Boolean = memberId == forMemberId || memberId in mentionIds

    fun isHiddenFor(memberId: String): Boolean = memberId in hiddenByIds

    fun photoPath(photoId: String): String = "goodNews/$authorId/$id/$photoId.jpg"

    fun thumbnailPath(photoId: String): String = "goodNews/$authorId/$id/$photoId-thumb.jpg"

    companion object {
        const val TEXT_MAX = 500
        const val PHOTOS_MAX = 3
        const val PAGE_SIZE = 20
    }
}
