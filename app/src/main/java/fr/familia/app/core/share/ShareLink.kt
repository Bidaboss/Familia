package fr.familia.app.core.share

/*
 * The link of a share (PRD, récit 31). It goes through a free Firebase Hosting page, which shows
 * no family data: the app opens the item for whoever has it (Android App Links, or the button of
 * the page). Only the kind and the id of the item are in the link; the security rules still decide
 * what each member reads. The page never shows the installation link: the family sends it to
 * whoever joins.
 */

/** The Hosting site of the Firebase project (firebase/hosting, published by « Publier Firebase »). */
const val SHARE_HOST = "familia-93286.web.app"

/** The custom scheme the button of the page opens (familia://o/event/abc), for the test app too. */
const val SHARE_SCHEME = "familia"

/** What a link points to. [path] is its segment in the link, never renamed once shared. */
enum class ShareKind(val path: String) {
    Event("event"),
    Poll("poll"),
    /** A birthday: the id is the member's. */
    Birthday("birthday"),
    List("list"),
    GoodNews("goodNews"),
    Trip("trip"),
    HelpRequest("help"),
    ;

    companion object {
        fun fromPath(path: String): ShareKind? = entries.firstOrNull { it.path == path }
    }
}

data class SharedLink(val kind: ShareKind, val id: String)

private val idPattern = Regex("[A-Za-z0-9_-]{1,64}")

/** « https://familia-93286.web.app/o/event/abc ». */
fun shareLink(kind: ShareKind, id: String): String {
    require(idPattern.matches(id)) { "Not a document id: $id" }
    return "https://$SHARE_HOST/o/${kind.path}/$id"
}

/**
 * The item of a link that opened the app, or null when it is not one of ours: another host, an
 * unknown kind, an id that is not a document id. [scheme], [host] and [path] come from the Uri.
 */
fun parseSharedLink(scheme: String?, host: String?, path: String?): SharedLink? {
    val segments = when {
        scheme == "https" && host == SHARE_HOST -> path.orEmpty().trim('/').split('/')
        // familia://o/event/abc: « o » is the host of the Uri, the rest its path.
        scheme == SHARE_SCHEME && host == "o" -> listOf("o") + path.orEmpty().trim('/').split('/')
        else -> return null
    }
    if (segments.size != 3 || segments[0] != "o") return null
    val kind = ShareKind.fromPath(segments[1]) ?: return null
    val id = segments[2].takeIf { idPattern.matches(it) } ?: return null
    return SharedLink(kind, id)
}

/**
 * Carte EXPERT-2: a content that shows children leaves Familia with its photo only when the one
 * who shares it is a responsible adult of every child it shows. [childParentIds] holds, for each
 * child shown, their responsible adults. Without children, the photo can go.
 */
fun photoAllowed(sharerId: String, childParentIds: Collection<Collection<String>>): Boolean =
    childParentIds.all { sharerId in it }
