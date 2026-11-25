package fr.familia.app.core.model

import java.time.Instant
import java.time.LocalDate

/** The occasion of a surprise (brief Cadeaux et Surprise, §3): Noël, Anniversaire or Autre. */
enum class SurpriseOccasion(val firestoreValue: String) {
    Christmas("christmas"),
    Birthday("birthday"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): SurpriseOccasion = entries.firstOrNull { it.firestoreValue == value } ?: Other
    }
}

/**
 * A surprise prepared for one person, its beneficiary, by its members (brief Cadeaux et Surprise du
 * 25/11). The beneficiary is never in [memberIds] and never reads it, even as admin principal.
 * « Prête » and « À confirmer » are not stored: they follow from the members and the date.
 */
data class Surprise(
    val id: String,
    val beneficiaryId: String,
    val title: String,
    val occasion: SurpriseOccasion,
    val date: LocalDate,
    val memberIds: List<String>,
    val creatorId: String,
    val creatorName: String,
) {
    companion object {
        const val MAX_TITLE = 60
    }
}

/** What the whole family but its beneficiary knows of a surprise in preparation, to join it. */
data class OpenSurprise(val id: String, val beneficiaryId: String, val title: String, val creatorName: String)

/** Only an adult starts a surprise (PRODUIT, 25/11); a child with the app is invited or joins. */
fun canCreateSurprise(member: Member): Boolean = member.profile == MemberProfile.Adult

/** Who may be in a surprise: a member with the app, a child with the app too (PRODUIT-19). */
fun canJoinSurprise(member: Member): Boolean = member.profile != MemberProfile.ChildWithoutApp

/** The members a surprise for [beneficiaryId] can be shown to: everyone with the app but them. */
fun surpriseCandidates(family: Family, beneficiaryId: String): List<Member> =
    family.members.filter { it.id != beneficiaryId && canJoinSurprise(it) }

/**
 * The members as saved: the picked ones still in the family, never the beneficiary, always the
 * creator, in the order of the family.
 */
fun surpriseMembers(family: Family, beneficiaryId: String, creatorId: String, picked: Set<String>): List<String> {
    val wanted = picked + creatorId
    return surpriseCandidates(family, beneficiaryId).map { it.id }.filter { it in wanted }
}

/** The day of [occasion] for [beneficiary]: their next birthday, the next 25 December, or none for « Autre ». */
fun surpriseDateFor(occasion: SurpriseOccasion, beneficiary: Member, today: LocalDate): LocalDate? = when (occasion) {
    SurpriseOccasion.Christmas -> LocalDate.of(today.year, 12, 25).let { if (it < today) it.plusYears(1) else it }
    SurpriseOccasion.Birthday -> beneficiary.birthDate?.let { nextBirthday(it, today) }
    SurpriseOccasion.Other -> null
}

/** The occasion a new surprise starts with: the birthday when it comes before Christmas, else Christmas. */
fun nearestSurpriseOccasion(beneficiary: Member, today: LocalDate): SurpriseOccasion {
    val birthday = surpriseDateFor(SurpriseOccasion.Birthday, beneficiary, today)
    val christmas = surpriseDateFor(SurpriseOccasion.Christmas, beneficiary, today)
    return if (birthday != null && christmas != null && birthday <= christmas) SurpriseOccasion.Birthday else SurpriseOccasion.Christmas
}

/** The age [beneficiary] turns on [date], for « Les 40 ans de… »; null without a birth date. */
fun ageOn(beneficiary: Member, date: LocalDate): Int? =
    beneficiary.birthDate?.let { date.year - it.year }?.takeIf { it >= 1 }

/** A wish of the beneficiary (always in the surprise) or an idea a member added (brief §4). */
enum class ProposalKind(val firestoreValue: String) {
    Wish("wish"),
    Idea("idea"),
    ;

    companion object {
        fun fromFirestore(value: String?): ProposalKind = if (value == "idea") Idea else Wish
    }
}

/**
 * One proposal of a surprise, at surprises/{sid}/proposals/{id}: `wish-{itemId}` for a wish of the
 * beneficiary, `idea-{entryId}` for an idea of the notebook. The sum and the count of the notes are
 * kept by the server: each note is read only by its author.
 */
data class SurpriseProposal(
    val id: String,
    val kind: ProposalKind,
    val title: String,
    val price: Double?,
    val url: String?,
    val authorId: String,
    val authorName: String,
    val removed: Boolean = false,
    val ratingSum: Int = 0,
    val ratingCount: Int = 0,
) {
    /** The average, shown from [MIN_RATINGS] notes only, so nobody guesses who gave which (PRODUIT, 25/11). */
    val average: Double? get() = if (ratingCount >= MIN_RATINGS) ratingSum.toDouble() / ratingCount else null

    companion object {
        const val MIN_RATINGS = 3
    }
}

fun wishProposalId(itemId: String): String = "wish-$itemId"

fun ideaProposalId(entryId: String): String = "idea-$entryId"

/** One gift already given to a person, for « Déjà offert à X », kept without limit. */
data class GiftHistoryItem(val id: String, val title: String, val year: Int, val occasion: SurpriseOccasion)

/** « 🎁 Je le prends » (solo) or « 👪 Cadeau commun » (shared, adults only), one per proposal. */
enum class TakeKind(val firestoreValue: String) {
    Solo("solo"),
    Shared("shared"),
    ;

    companion object {
        fun fromFirestore(value: String?): TakeKind = entries.firstOrNull { it.firestoreValue == value } ?: Solo
    }
}

/**
 * Someone took a proposal (surprises/{sid}/takes/{pid}): alone, or as a joint gift of [goal] euros.
 * [sharesTotal] and [sharesCount] are kept by the server: the members never read the others' parts.
 */
data class SurpriseTake(
    val proposalId: String,
    val kind: TakeKind,
    val ownerId: String,
    val boughtBy: String? = null,
    val goal: Int? = null,
    val sharesTotal: Int = 0,
    val sharesCount: Int = 0,
) {
    /** What is still missing of a joint gift, never below 0. */
    val missing: Int get() = ((goal ?: 0) - sharesTotal).coerceAtLeast(0)

    companion object {
        /** A part is in whole euros, from 1 to 10 000 (the rules). */
        const val MAX_AMOUNT = 10_000
    }
}

/** My line in a surprise (surprises/{sid}/members/{memberId}). « Je ne participe pas » is final. */
data class SurpriseMemberRow(val memberId: String, val validated: Boolean = false, val declined: Boolean = false, val muted: Boolean = false)

/** The goal of a joint gift from the price: whole euros, rounded up, 1 at least. */
fun jointGoal(price: Double?): Int = price?.let { kotlin.math.ceil(it).toInt() }?.coerceIn(1, SurpriseTake.MAX_AMOUNT) ?: 50

/**
 * From the day itself (Paris), a surprise waits for « C'est offert » (brief §6): its tile turns to
 * « Surprise à confirmer » and the big orange card shows on top of it.
 */
fun Surprise.isToConfirm(today: java.time.LocalDate): Boolean = !today.isBefore(date)

/**
 * For the Accueil (« À répondre », récit 113 with the fil principal): how many of my surprises wait
 * for « C'est offert » today, and the first one to open.
 */
data class SurprisesToConfirm(val count: Int, val firstTitle: String, val firstId: String)

fun surprisesToConfirm(mine: List<Surprise>, today: java.time.LocalDate): SurprisesToConfirm? {
    val due = mine.filter { it.isToConfirm(today) }.sortedBy { it.date }
    val first = due.firstOrNull() ?: return null
    return SurprisesToConfirm(due.size, first.title, first.id)
}

/**
 * Administration > « Surprises offertes récemment » (brief § 9): the title, the date and the person,
 * never what was inside. Written by the server at « C'est offert », kept until the purge.
 */
data class OfferedSurprise(
    val id: String,
    val title: String,
    val date: LocalDate?,
    val beneficiaryName: String,
    val offeredAt: Instant?,
    /** « Annuler C'est offert » asked: the server puts it back « À confirmer ». */
    val cancelAsked: Boolean,
)

/** The last offered first. */
fun sortOffered(list: List<OfferedSurprise>): List<OfferedSurprise> =
    list.sortedWith(compareByDescending<OfferedSurprise> { it.offeredAt }.thenByDescending { it.date })
