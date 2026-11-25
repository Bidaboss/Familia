package fr.familia.app.feature.members

import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.nextBirthday
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/*
 * The Famille tab, « Foyers » (brief Famille-recettes F1, l'admin le 25/11): a classic list, one block
 * per household, all unfolded, « Mon foyer » first. Pure, so the tests check it without Compose.
 */

/** One block of the list; [mine] is titled « Mon foyer ». */
internal data class HouseholdSection(val group: HouseholdGroup, val mine: Boolean)

/**
 * My household first, then the others in the order of [groups] (already sorted by householdGroups,
 * « Autres » last). Without a household of mine, the order stays as it is.
 */
internal fun householdSections(groups: List<HouseholdGroup>, meId: String): List<HouseholdSection> {
    val mine = groups.firstOrNull { group -> group.household != null && group.members.any { it.id == meId } }
    return listOfNotNull(mine?.let { HouseholdSection(it, mine = true) }) +
        groups.filter { it !== mine }.map { HouseholdSection(it, mine = false) }
}

/** How far ahead a row says « 🎂 Dans n jours » in place of the profile. */
internal const val BIRTHDAY_SOON_DAYS = 14L

/** Days until the next birthday of [member] when it is within [BIRTHDAY_SOON_DAYS], 0 for today; else null. */
internal fun birthdaySoonIn(member: Member, today: LocalDate): Int? {
    val born = member.birthDate ?: return null
    val days = ChronoUnit.DAYS.between(today, nextBirthday(born, today))
    return days.takeIf { it in 0..BIRTHDAY_SOON_DAYS }?.toInt()
}

/** The age shown after « Enfant », null without a birth date. */
internal fun ageOf(member: Member, today: LocalDate): Int? =
    member.birthDate?.takeIf { !it.isAfter(today) }?.let { Period.between(it, today).years }

/** Appeler and Message: on every adult but oneself, once they gave a phone number (récit 66). */
internal fun reachable(member: Member, meId: String): Boolean =
    member.profile == MemberProfile.Adult && member.id != meId && !member.phone.isNullOrBlank()

/** « 13 personnes · 4 foyers »: « Autres » is not a household. */
internal fun familyCounts(groups: List<HouseholdGroup>): Pair<Int, Int> =
    groups.sumOf { it.members.size } to groups.count { it.household != null }
