package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BirthdayTest {

    private fun member(id: String, birth: LocalDate?) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, birth, "h1", MemberProfile.Adult, false, emptyList(), null)

    @Test
    fun `a birthday today is today, not next year`() {
        val today = LocalDate.of(2026, 7, 19)
        assertEquals(today, nextBirthday(LocalDate.of(2015, 7, 19), today))
    }

    @Test
    fun `a birthday already past this year comes back next year`() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(LocalDate.of(2027, 4, 29), nextBirthday(LocalDate.of(2021, 4, 29), today))
    }

    @Test
    fun `someone born on 29 February celebrates on 28 February in other years`() {
        val birth = LocalDate.of(2016, 2, 29)
        assertEquals(LocalDate.of(2027, 2, 28), nextBirthday(birth, LocalDate.of(2026, 11, 14)))
        assertEquals(LocalDate.of(2028, 2, 29), nextBirthday(birth, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `upcoming birthdays are nearest first, with the age they turn`() {
        val today = LocalDate.of(2026, 11, 14)
        val list = upcomingBirthdays(
            listOf(
                member("later", LocalDate.of(1960, 3, 2)),
                member("soon", LocalDate.of(2010, 11, 20)),
                member("today", LocalDate.of(1990, 11, 14)),
            ),
            today,
        )
        assertEquals(listOf("today", "soon", "later"), list.map { it.member.id })
        assertEquals(listOf(36, 16, 67), list.map { it.age })
        assertEquals(LocalDate.of(2027, 3, 2), list.last().date)
    }

    @Test
    fun `members without a birth date, or with one in the future, have no birthday`() {
        val today = LocalDate.of(2026, 11, 14)
        val list = upcomingBirthdays(
            listOf(member("none", null), member("future", LocalDate.of(2030, 1, 1)), member("baby", today)),
            today,
        )
        assertEquals(emptyList<String>(), list.map { it.member.id })
    }
}
