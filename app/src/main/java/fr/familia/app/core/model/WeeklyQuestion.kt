package fr.familia.app.core.model

import java.time.Instant

/** The longest answer to the question of the week (same limit as the rules). */
const val WEEKLY_ANSWER_MAX = 500

/**
 * The question of the week (PRD, récit 37), published by the server each Monday at 9 h. [id] is the
 * date of that Monday (AAAA-MM-JJ). [answerCount] counts the answers, never « Passer » nor names.
 */
data class WeeklyQuestion(
    val id: String,
    val text: String,
    val proposedByName: String?,
    val publishedAt: Instant,
    val revealAt: Instant,
    val answerCount: Int,
) {
    /** Everyone reads the answers from [revealAt], 48 h after the question. */
    fun isRevealed(now: Instant): Boolean = !now.isBefore(revealAt)
}

/** One member's answer; [skipped] is « Passer », with no [text]. [photoId] is its optional photo. */
data class WeeklyAnswer(
    val memberId: String,
    val memberName: String,
    val text: String?,
    val skipped: Boolean,
    val updatedAt: Instant?,
    val photoId: String? = null,
)

/** Storage: weeklyQuestions/{questionId}/{memberId}/{photoId}.jpg, as in storage.rules. */
fun weeklyPhotoPath(questionId: String, memberId: String, photoId: String) = "weeklyQuestions/$questionId/$memberId/$photoId.jpg"

fun weeklyThumbnailPath(questionId: String, memberId: String, photoId: String) =
    "weeklyQuestions/$questionId/$memberId/$photoId-thumb.jpg"
