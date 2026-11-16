package fr.familia.app.core.model

import java.time.Instant

/**
 * « Une idée ? Un souci ? » (PRD, récit 6 sexies): what a member sends to the admin principal.
 * Only its author and the admin principal read it. The admin principal moves it through [Status],
 * with an optional [note], and its author is told each time.
 */
data class Feedback(
    val id: String,
    val kind: Kind,
    val text: String,
    val authorId: String,
    val authorName: String,
    val context: FeedbackContext,
    val createdAt: Instant?,
    val status: Status = Status.New,
    val note: String? = null,
    val statusAt: Instant? = null,
    /** The optional screenshot, feedback/{authorId}/{id}.jpg in Storage, seen by the same two people. */
    val screenshot: String? = null,
) {
    enum class Kind(val key: String) {
        Idea("idea"),
        Problem("problem"),
        ;

        companion object {
            fun fromKey(key: String?): Kind? = entries.firstOrNull { it.key == key }
        }
    }

    enum class Status(val key: String) {
        New("new"),
        Planned("planned"),
        Done("done"),
        Declined("declined"),
        ;

        companion object {
            fun fromKey(key: String?): Status = entries.firstOrNull { it.key == key } ?: New
        }
    }

    companion object {
        const val MAX_TEXT = 1000
        const val MAX_NOTE = 300

        /** The screenshot is at most 150 KB (firebase/storage.rules). */
        const val SCREENSHOT_MAX_SIDE = 1280
        const val SCREENSHOT_TARGET_BYTES = 140 * 1024
        const val SCREENSHOT_LIMIT_BYTES = 150 * 1024

        fun screenshotPath(authorId: String, id: String): String = "feedback/$authorId/$id.jpg"
    }
}

/** Added on its own when sending, so the admin principal knows where it came from. */
data class FeedbackContext(
    val appVersion: String,
    /** « 14 » */
    val android: String,
    /** « Google Pixel 8 » */
    val device: String,
    val screen: FeedbackOrigin,
)

/** Where the form was opened. */
enum class FeedbackOrigin(val key: String) {
    Family("family"),
    Help("help"),
    ;

    companion object {
        fun fromKey(key: String?): FeedbackOrigin = entries.firstOrNull { it.key == key } ?: Family
    }
}

/** How many are still « Nouvelle »: the counter of the « Retours » card. */
fun newCount(all: List<Feedback>): Int = all.count { it.status == Feedback.Status.New }

/** Newest first; one being sent (no server time yet) on top. */
fun feedbackOrder(all: List<Feedback>): List<Feedback> =
    all.sortedWith(compareByDescending<Feedback> { it.createdAt ?: Instant.MAX }.thenBy { it.id })
