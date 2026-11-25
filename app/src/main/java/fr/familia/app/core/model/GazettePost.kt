package fr.familia.app.core.model

import java.time.Duration
import java.time.Instant

/**
 * « Dire où je suis » (récit 117): the place of a publication. [lat] and [lng] are there only while
 * it is shared, 2 h at most; once stopped or over, only [label] stays (« était à Lyon »).
 */
data class PostPlace(val label: String, val lat: Double? = null, val lng: Double? = null) {
    val hasPoint: Boolean get() = lat != null && lng != null

    companion object {
        const val LABEL_MAX = 80

        /** PRD 117: shared 2 h at most. */
        val SHARE: Duration = Duration.ofHours(2)
    }
}

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
    /** « Dire où je suis », or null. */
    val place: PostPlace? = null,
) {
    val isEdited: Boolean get() = editedAt != null

    /** When the sharing of [place] ends: 2 h after the publication. */
    val shareEndsAt: Instant? get() = place?.takeIf { it.hasPoint }?.let { (createdAt ?: Instant.now()).plus(PostPlace.SHARE) }

    /** The position still shows: it has its point and its 2 h are not over. */
    fun isSharing(now: Instant): Boolean = shareEndsAt?.isAfter(now) == true

    fun photoPath(photoId: String): String = "gazettePosts/$authorId/$id/$photoId.jpg"

    fun thumbnailPath(photoId: String): String = "gazettePosts/$authorId/$id/$photoId-thumb.jpg"

    companion object {
        /** PRD 119 b: 1 000 characters and 6 photos at most. */
        const val TEXT_MAX = 1000
        const val PHOTOS_MAX = 6
        const val MENTIONS_MAX = 20
    }
}

/**
 * Who may remove [post], or stop the sharing of its position (récits 117 et 121, règle du 25/11 comme
 * pour les photos d'album): its author, an adult who looks after its child author ([caresFor]:
 * a responsible adult or a chef de famille of its household), the admin principal.
 */
fun canRemoveGazettePost(post: GazettePost, me: Member, family: Family): Boolean =
    post.authorId == me.id ||
        family.isOwner(me) ||
        family.members.firstOrNull { it.id == post.authorId }?.let { caresFor(me, it) } == true

/** Who may be named in a publication (PRD 119 c): the others who have the app, children with it too. */
fun gazetteMentionCandidates(me: Member, family: Family): List<Member> =
    family.members
        .filter { it.id != me.id && it.profile != MemberProfile.ChildWithoutApp }
        .sortedBy { it.firstName.lowercase() }
