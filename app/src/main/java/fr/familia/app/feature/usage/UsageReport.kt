package fr.familia.app.feature.usage

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.UsageDay
import fr.familia.app.core.model.UsageScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Seconds spent on one part of the app over 7 and 30 days. */
data class ScreenTime(val screen: UsageScreen, val seconds7: Int, val seconds30: Int)

/** One member, for the admin principal (PRD, récit 1 nonies, carte PRODUIT-54) or a responsible adult. */
data class PersonUsage(
    val member: Member,
    /** null: never opened the app in the 13 months kept (« Jamais connecté »). */
    val lastDay: LocalDate?,
    val seconds7: Int,
    val seconds30: Int,
    /** Over 30 days, most used first; empty for a responsible adult, who sees the totals only. */
    val screens: List<ScreenTime>,
)

/** « Santé de la famille » (carte ENG-4): good news published in a week, and the reactions they got. */
data class WeekHealth(val weekStart: LocalDate, val posts: Int, val reactions: Int)

data class FamilyUsage(
    val active7: Int,
    val active30: Int,
    /** Members who have the app: the ceiling of the two counts above. */
    val withApp: Int,
    /** Openings of the app per day, over the last 30 days, oldest first. */
    val opensPerDay: List<Pair<LocalDate, Int>>,
    /** Most used first over 30 days. */
    val screens: List<ScreenTime>,
    /** The last 4 weeks, oldest first. */
    val health: List<WeekHealth>,
)

sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object Error : UsageUiState
    /** [family] is null for a responsible adult, who only sees their children. */
    data class Ready(val family: FamilyUsage?, val people: List<PersonUsage>) : UsageUiState
}

object UsageReport {
    const val DAYS = 30L
    const val WEEKS = 4L

    /** Members who can open the app; a child without the app has nothing to count. */
    fun counted(members: List<Member>): List<Member> =
        members.filter { it.profile != MemberProfile.ChildWithoutApp }.sortedBy { it.firstName.lowercase() }

    /** The children of [adultId] who have the app (carte EXPERT-5). */
    fun childrenOf(members: List<Member>, adultId: String): List<Member> =
        counted(members).filter { it.profile != MemberProfile.Adult && adultId in it.parentIds }

    /** The first day of the 30 read, today included. */
    fun since(today: LocalDate): LocalDate = today.minusDays(DAYS - 1)

    private fun within(day: LocalDate, today: LocalDate, days: Long) = !day.isBefore(today.minusDays(days - 1)) && !day.isAfter(today)

    private fun screenTimes(days: List<UsageDay>, today: LocalDate): List<ScreenTime> =
        UsageScreen.entries.map { screen ->
            ScreenTime(
                screen,
                seconds7 = days.filter { within(it.day, today, 7) }.sumOf { it.usage.seconds[screen.key] ?: 0 },
                seconds30 = days.filter { within(it.day, today, DAYS) }.sumOf { it.usage.seconds[screen.key] ?: 0 },
            )
        }.filter { it.seconds30 > 0 }.sortedByDescending { it.seconds30 }

    private fun used(day: UsageDay) = day.usage.opens > 0 || day.usage.totalSeconds > 0

    /**
     * The report of [members] from their [days] (the last 30 at least) and, for those with no
     * recent day, the [olderLastDays] read further back. [goodNews] gives the « santé de la famille »;
     * [full] is false for a responsible adult, who sees totals and nothing else.
     */
    fun build(
        members: List<Member>,
        days: List<UsageDay>,
        olderLastDays: Map<String, LocalDate?>,
        goodNews: List<GoodNews>,
        today: LocalDate,
        full: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): UsageUiState.Ready {
        val recent = days.filter { within(it.day, today, DAYS) && used(it) }
        val byMember = recent.groupBy { it.memberId }
        val people = members.map { member ->
            val mine = byMember[member.id].orEmpty()
            PersonUsage(
                member = member,
                lastDay = mine.maxOfOrNull { it.day } ?: olderLastDays[member.id],
                seconds7 = mine.filter { within(it.day, today, 7) }.sumOf { it.usage.totalSeconds },
                seconds30 = mine.sumOf { it.usage.totalSeconds },
                screens = if (full) screenTimes(mine, today) else emptyList(),
            )
        }
        if (!full) return UsageUiState.Ready(family = null, people = people)
        val ids = members.map { it.id }.toSet()
        val ours = recent.filter { it.memberId in ids }
        val family = FamilyUsage(
            active7 = ours.filter { within(it.day, today, 7) }.map { it.memberId }.toSet().size,
            active30 = ours.map { it.memberId }.toSet().size,
            withApp = members.size,
            opensPerDay = (DAYS - 1 downTo 0).map { back ->
                val day = today.minusDays(back)
                day to ours.filter { it.day == day }.sumOf { it.usage.opens }
            },
            screens = screenTimes(ours, today),
            health = health(goodNews, today, zone),
        )
        return UsageUiState.Ready(family, people)
    }

    /** Weeks start on Monday, as in the planning. */
    fun health(goodNews: List<GoodNews>, today: LocalDate, zone: ZoneId): List<WeekHealth> {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (WEEKS - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back)
            val posts = goodNews.filter { news ->
                val day = news.createdAt?.atZone(zone)?.toLocalDate() ?: return@filter false
                !day.isBefore(start) && day.isBefore(start.plusWeeks(1))
            }
            WeekHealth(start, posts.size, posts.sumOf { it.reactions.size })
        }
    }
}
