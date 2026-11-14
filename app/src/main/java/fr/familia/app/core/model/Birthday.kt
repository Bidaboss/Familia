package fr.familia.app.core.model

import java.time.LocalDate

/** A member's next birthday, computed from their birth date: nothing is stored (plan, "Les données"). */
data class Birthday(val member: Member, val date: LocalDate, val age: Int)

/**
 * First day on or after [from] when someone born on [birthDate] celebrates. Someone born on
 * 29 February celebrates on 28 February in the other years (java.time moves it to the last day).
 */
fun nextBirthday(birthDate: LocalDate, from: LocalDate): LocalDate {
    val thisYear = birthDate.withYear(from.year)
    return if (thisYear < from) birthDate.withYear(from.year + 1) else thisYear
}

/**
 * The next birthday of every member who has a birth date, nearest first. A birth date in the
 * future (a typing mistake) gives no birthday rather than a negative age.
 */
fun upcomingBirthdays(members: List<Member>, from: LocalDate): List<Birthday> =
    members
        .mapNotNull { member ->
            val birth = member.birthDate ?: return@mapNotNull null
            val date = nextBirthday(birth, from)
            val age = date.year - birth.year
            if (age < 1) null else Birthday(member, date, age)
        }
        .sortedWith(compareBy<Birthday> { it.date }.thenBy { it.member.firstName })
