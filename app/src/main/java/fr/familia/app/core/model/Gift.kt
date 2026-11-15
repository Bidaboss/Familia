package fr.familia.app.core.model

import java.time.LocalDate

/** The three kinds of gift lists (PRD, récit 7). See docs/data-model.md, « Listes et cadeaux ». */
enum class GiftListKind(val firestoreValue: String) {
    /** « Mes envies »: one permanent list per member, open to the whole family. */
    Wishes("wishes"),

    /** A list for an occasion, made by its beneficiary (or for a child without the app). */
    Own("own"),

    /** A list made for another member, who never sees it before the reveal. */
    Surprise("surprise"),
    ;

    companion object {
        fun fromFirestore(value: String?): GiftListKind? = entries.firstOrNull { it.firestoreValue == value }
    }
}

enum class GiftOccasion(val firestoreValue: String) {
    Christmas("christmas"),
    Birthday("birthday"),
    Birth("birth"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): GiftOccasion? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** A gift list. [occasion] and [date] are null for « Mes envies », which [viewerIds] leaves empty. */
data class GiftList(
    val id: String,
    val kind: GiftListKind,
    val title: String,
    val occasion: GiftOccasion?,
    val date: LocalDate?,
    val beneficiaryId: String,
    val viewerIds: List<String>,
    val creatorId: String,
    val creatorName: String,
    /** Set by the server the day after the occasion: the list is archived and nothing is hidden any more. */
    val revealed: Boolean = false,
)

/** The id of the « Mes envies » list of [memberId]: there is exactly one. */
fun wishesListId(memberId: String): String = "wishes-$memberId"

/** An item of a list. [visibleToBeneficiary] is false for a surprise idea added by someone else. */
data class GiftItem(
    val id: String,
    val title: String,
    val url: String?,
    /** Indicative price in euros. */
    val price: Double?,
    val note: String?,
    val authorId: String,
    val authorName: String,
    val visibleToBeneficiary: Boolean,
    val imageUrl: String? = null,
)

enum class ClaimType(val firestoreValue: String) {
    /** « Je le prends ». */
    Solo("solo"),

    /** A joint gift: [GiftClaim.holderId] is its responsible, the others declare their part. */
    Shared("shared"),
    ;

    companion object {
        fun fromFirestore(value: String?): ClaimType? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** The reservation of the item [itemId] (same id). Never readable by the beneficiary before the reveal. */
data class GiftClaim(val itemId: String, val type: ClaimType, val holderId: String, val holderName: String)

/** The part of [memberId] in a joint gift, in euros. The responsible marks it [received]. */
data class GiftShare(val memberId: String, val memberName: String, val amount: Double, val received: Boolean)
