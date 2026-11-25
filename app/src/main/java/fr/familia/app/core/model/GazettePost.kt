package fr.familia.app.core.model

import java.time.Instant

/**
 * Une publication de la gazette (récits 115, 117, 119 b et 121): a word, up to 6 photos, or both,
 * with the members named. The whole family sees it. Its author changes the text (« modifié »);
 * its author, a responsible adult of its author and the admin principal remove it.
 */
data class GazettePost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val photoIds: List<String> = emptyList(),
    val mentionIds: List<String> = emptyList(),
    /** Null until the server has written it (just published, offline). */
    val createdAt: Instant? = null,
    /** Set when its author changed the text. */
    val editedAt: Instant? = null,
    /** Published on this phone without network: it goes by itself when the network is back. */
    val pending: Boolean = false,
) {
    val isEdited: Boolean get() = editedAt != null

    fun photoPath(photoId: String): String = "gazettePosts/$authorId/$id/$photoId.jpg"

    fun thumbnailPath(photoId: String): String = "gazettePosts/$authorId/$id/$photoId-thumb.jpg"

    companion object {
        /** PRD 119 b: 1 000 characters and 6 photos at most. */
        const val TEXT_MAX = 1000
        const val PHOTOS_MAX = 6
        const val MENTIONS_MAX = 20
    }
}

/** Who may remove [post] (récit 121): its author, a responsible adult of its author, the admin principal. */
fun canRemoveGazettePost(post: GazettePost, me: Member, family: Family): Boolean =
    post.authorId == me.id ||
        family.isOwner(me) ||
        family.members.firstOrNull { it.id == post.authorId }?.parentIds?.contains(me.id) == true

/** Who may be named in a publication (PRD 119 c): the others who have the app, children with it too. */
fun gazetteMentionCandidates(me: Member, family: Family): List<Member> =
    family.members
        .filter { it.id != me.id && it.profile != MemberProfile.ChildWithoutApp }
        .sortedBy { it.firstName.lowercase() }
