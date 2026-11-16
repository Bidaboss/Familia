package fr.familia.app.core.model

import java.time.Instant

/**
 * « Qui est-ce ? » (PRD, récit 43): an old photo scanned by a member. The family guesses who is on
 * it and the year; the answer shows once one has guessed. No score. See docs/data-model.md.
 */
data class WhoIsItPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    /** « Un mariage, au printemps »: may be empty. */
    val hint: String,
    val createdAt: Instant?,
) {
    val photoPath: String get() = "whoIsIt/$authorId/$id/photo.jpg"
    val thumbPath: String get() = "whoIsIt/$authorId/$id/photo-thumb.jpg"

    companion object {
        const val MAX_HINT = 140
        const val MAX_WHO = 80
        const val FIRST_YEAR = 1850
    }
}

/** Who is on the photo and when, written by its author; [personId] is someone of the tree (récit 40). */
data class WhoIsItAnswer(val who: String, val year: Int?, val personId: String? = null)

/** One guess, in the name of [memberId]; an empty [who] is « Je ne sais pas ». Never changed. */
data class WhoIsItGuess(val memberId: String, val name: String, val who: String, val year: Int?)

/** A year typed as text: blank gives no year, anything else must be a year the rules accept. */
fun parseWhoIsItYear(text: String, thisYear: Int): Result<Int?> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    val year = trimmed.toIntOrNull()
    return if (year != null && year in WhoIsItPost.FIRST_YEAR..thisYear) Result.success(year) else Result.failure(IllegalArgumentException(trimmed))
}
