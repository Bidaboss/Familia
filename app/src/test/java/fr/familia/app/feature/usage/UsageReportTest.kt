package fr.familia.app.feature.usage

import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.UsageDay
import fr.familia.app.core.model.UsageScreen
import fr.familia.app.feature.goodnews.goodNews
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageReportTest {
    private val today = LocalDate.of(2026, 11, 21)

    private fun member(id: String, profile: MemberProfile = MemberProfile.Adult, parents: List<String> = emptyList()) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, null, "h1", profile, false, parents, null)

    private val adult = member("alix")
    private val other = member("bruno")
    private val child = member("chloe", MemberProfile.ChildWithApp, listOf("alix"))
    private val baby = member("dany", MemberProfile.ChildWithoutApp, listOf("alix"))

    private fun day(member: Member, back: Long, opens: Int, vararg seconds: Pair<UsageScreen, Int>) =
        UsageDay(member.id, today.minusDays(back), DayUsage(opens, seconds.associate { (s, n) -> s.key to n }))

    @Test
    fun onlyMembersWithTheAppAreCounted() {
        assertEquals(listOf("alix", "bruno", "chloe"), UsageReport.counted(listOf(other, baby, child, adult)).map { it.id })
        assertEquals(listOf("chloe"), UsageReport.childrenOf(listOf(other, baby, child, adult), "alix").map { it.id })
        assertTrue(UsageReport.childrenOf(listOf(other, baby, child, adult), "bruno").isEmpty())
    }

    @Test
    fun theFamilyAndEachPersonOverSevenAndThirtyDays() {
        val days = listOf(
            day(adult, 0, 2, UsageScreen.Planning to 600, UsageScreen.Home to 60),
            day(adult, 10, 1, UsageScreen.Planning to 300),
            day(child, 3, 4, UsageScreen.Gifts to 800),
            day(child, 40, 1, UsageScreen.Gifts to 999),
        )
        val members = listOf(adult, other, child)
        val report = UsageReport.build(members, days, mapOf("bruno" to null), emptyList(), today, full = true)
        val family = report.family!!
        assertEquals(2, family.active7)
        assertEquals(2, family.active30)
        assertEquals(3, family.withApp)
        assertEquals(30, family.opensPerDay.size)
        assertEquals(today to 2, family.opensPerDay.last())
        assertEquals(listOf(UsageScreen.Planning, UsageScreen.Gifts, UsageScreen.Home), family.screens.map { it.screen })
        assertEquals(ScreenTime(UsageScreen.Planning, 600, 900), family.screens.first())

        val alix = report.people.first { it.member.id == "alix" }
        assertEquals(today, alix.lastDay)
        assertEquals(660, alix.seconds7)
        assertEquals(960, alix.seconds30)
        val bruno = report.people.first { it.member.id == "bruno" }
        assertNull(bruno.lastDay)
        assertEquals(0, bruno.seconds30)
    }

    @Test
    fun anOlderVisitIsShownWhenNothingIsRecent() {
        val report = UsageReport.build(listOf(other), emptyList(), mapOf("bruno" to today.minusDays(90)), emptyList(), today, full = true)
        assertEquals(today.minusDays(90), report.people.single().lastDay)
    }

    @Test
    fun aResponsibleAdultSeesTotalsOnly() {
        val report = UsageReport.build(listOf(child), listOf(day(child, 1, 3, UsageScreen.Gifts to 120)), emptyMap(), emptyList(), today, full = false)
        assertNull(report.family)
        val chloe = report.people.single()
        assertEquals(120, chloe.seconds7)
        assertTrue(chloe.screens.isEmpty())
    }

    @Test
    fun theHealthOfTheFamilyCountsGoodNewsAndReactionsPerWeek() {
        // 21/11/2026 is a Saturday: this week starts on Monday 16/11.
        val news = listOf(
            goodNews("n1", adult, daysAgo = 0).copy(reactions = mapOf("bruno" to Reaction.entries.first(), "chloe" to Reaction.entries.first())),
            goodNews("n2", other, daysAgo = 6),
            goodNews("n3", other, daysAgo = 30),
        )
        val weeks = UsageReport.health(news, today, ZoneOffset.UTC)
        assertEquals(4, weeks.size)
        assertEquals(WeekHealth(LocalDate.of(2026, 11, 16), 1, 2), weeks.last())
        assertEquals(WeekHealth(LocalDate.of(2026, 11, 9), 1, 0), weeks[2])
        assertEquals(0, weeks.first().posts)
    }
}
