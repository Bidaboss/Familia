package fr.familia.app.core.model

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
