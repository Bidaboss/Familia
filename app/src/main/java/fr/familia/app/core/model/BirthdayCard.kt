package fr.familia.app.core.model

import java.time.Instant

/**
 * A card of vœux (PRD, récit 36, avis EXPERT n° 11): a short word to the member celebrated, one per
 * author and per birthday, in birthdayCards/{year}-{forMemberId}-{authorId}.
 */
data class BirthdayCard(
    val forMemberId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val year: Int,
    val createdAt: Instant? = null,
) {
    val id: String get() = id(year, forMemberId, authorId)

    companion object {
        const val TEXT_MAX = 280

        fun id(year: Int, forMemberId: String, authorId: String): String = "$year-$forMemberId-$authorId"

        /** The Bonne nouvelle that Familia publishes on the morning of the birthday: cards may be written while it is new. */
        fun newsId(year: Int, memberId: String): String = "birthday-$year-$memberId"

        /** The year of such a Bonne nouvelle, read back from its id; null for any other. */
        fun yearOfNews(newsId: String): Int? =
            newsId.takeIf { it.startsWith("birthday-") }?.removePrefix("birthday-")?.substringBefore('-')?.toIntOrNull()
    }
}
