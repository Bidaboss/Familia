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

    /**
     * « Idées de la famille » (récit 9 ter): one permanent list per member, ideas-{memberId}, that the
     * whole family but the person reads and adds to. Its ideas are never visible to the person.
     */
    Ideas("ideas"),
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

/** The id of the « Idées de la famille » of [memberId]: there is exactly one (récit 9 ter). */
fun ideasListId(memberId: String): String = "ideas-$memberId"

/** The two lists every member keeps all year (récit 9 ter). */
val GiftListKind.isPermanent: Boolean get() = this == GiftListKind.Wishes || this == GiftListKind.Ideas

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
    /** The occasion a wish is for (récit 9 ter): Christmas or a birthday; null for « Quand tu veux ». */
    val occasion: GiftOccasion? = null,
    /** When it was added, for the « nouvelles » of the Cadeaux tab; null while the server has not written it. */
    val addedAt: java.time.Instant? = null,
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
/**
 * The reservation of an item. For a joint gift, [sharesTotal] and [sharesCount] are kept by the
 * server: the participants see the total, only the responsible sees each part (PRD rév. 151).
 */
data class GiftClaim(
    val itemId: String,
    val type: ClaimType,
    val holderId: String,
    val holderName: String,
    val sharesTotal: Double = 0.0,
    val sharesCount: Int = 0,
    /** « Mes envies » only: the occasion its holder chose, the next one of this kind (PRD rév. 186). */
    val occasion: GiftOccasion? = null,
    /** The day it was made, in Paris: without a choice, its occasion counts from that day, as on the server. */
    val createdOn: java.time.LocalDate? = null,
)

/**
 * The occasion a reservation of « Mes envies » is meant for without a choice: the beneficiary's
 * birthday or Christmas, whichever comes first after [day]. The server decides with the day of the
 * reservation (wishesReveal.ts); this is what the app shows meanwhile.
 */
fun defaultWishOccasion(birthDate: LocalDate?, day: LocalDate): GiftOccasion {
    val christmasThisYear = LocalDate.of(day.year, 12, 25)
    val christmas = if (day <= christmasThisYear) christmasThisYear else christmasThisYear.plusYears(1)
    if (birthDate == null) return GiftOccasion.Christmas
    val thisYear = runCatching { birthDate.withYear(day.year) }.getOrElse { LocalDate.of(day.year, 2, 28) }
    val birthday = if (thisYear >= day) thisYear else runCatching { birthDate.withYear(day.year + 1) }.getOrElse { LocalDate.of(day.year + 1, 2, 28) }
    return if (birthday < christmas) GiftOccasion.Birthday else GiftOccasion.Christmas
}

/** The part of [memberId] in a joint gift, in euros. The responsible marks it [received]. */
data class GiftShare(val memberId: String, val memberName: String, val amount: Double, val received: Boolean)

/**
 * A wish someone gave, in « On m'a offert » (PRD, récit 10 bis): written by the server the day
 * after the occasion, with the first names of the givers and never their amounts.
 */
data class ReceivedGift(
    val id: String,
    val title: String,
    val url: String?,
    val imageUrl: String?,
    val givers: List<String>,
    val joint: Boolean,
    val occasion: GiftOccasion?,
    val year: Int?,
    val thanked: Boolean,
)
