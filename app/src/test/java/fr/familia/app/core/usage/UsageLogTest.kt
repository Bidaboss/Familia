package fr.familia.app.core.usage

import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.UsageScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLogTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val hour = 60 * 60_000L

    @Test
    fun secondsAddUpPerScreenAndDay() {
        val log = UsageLog(memberId = "m1")
            .addSeconds(today, UsageScreen.Planning, 30)
            .addSeconds(today, UsageScreen.Planning, 45)
            .addSeconds(today, UsageScreen.Home, 10)
            .addSeconds(today, UsageScreen.Home, 0)
        assertEquals(DayUsage(0, mapOf("planning" to 75, "home" to 10)), log.days[today.toString()]!!.toDayUsage())
    }

    @Test
    fun aQuickSwitchBackIsNotANewOpening() {
        val start = 1_000_000L
        val log = UsageLog(memberId = "m1")
            .started(today, start)
            .stopped(start + 60_000)
            .started(today, start + 2 * 60_000)
            .stopped(start + 3 * 60_000)
            .started(today, start + 3 * 60_000 + UsageLog.REOPEN_AFTER_MILLIS)
        assertEquals(2, log.days[today.toString()]!!.opens)
    }

    @Test
    fun anotherMemberStartsFromNothing() {
        val log = UsageLog(memberId = "m1").addSeconds(today, UsageScreen.Home, 10)
        assertEquals(log, log.forMember("m1"))
        assertTrue(log.forMember("m2").days.isEmpty())
    }

    @Test
    fun aFinishedDayIsSentOnceAndTodayAtMostEveryThreeHours() {
        val yesterday = today.minusDays(1)
        val now = 10 * hour
        var log = UsageLog(memberId = "m1")
            .addSeconds(yesterday, UsageScreen.Home, 60)
            .addSeconds(today, UsageScreen.Planning, 30)
        assertEquals(setOf(yesterday, today), log.toSend(today, now).keys)

        log.toSend(today, now).forEach { (day, usage) -> log = log.markSent(day, usage, today, now) }
        assertTrue(log.toSend(today, now).isEmpty())

        log = log.addSeconds(today, UsageScreen.Planning, 30)
        assertTrue(log.toSend(today, now + hour).isEmpty())
        assertEquals(setOf(today), log.toSend(today, now + 3 * hour).keys)
    }

    @Test
    fun aDayThatChangedWhileSendingIsSentAgain() {
        val yesterday = today.minusDays(1)
        var log = UsageLog(memberId = "m1").addSeconds(yesterday, UsageScreen.Home, 60)
        val sending = log.toSend(today, 0)[yesterday]!!
        log = log.addSeconds(yesterday, UsageScreen.Home, 5).markSent(yesterday, sending, today, 0)
        assertEquals(65, log.toSend(today, 0)[yesterday]!!.seconds["home"])
    }

    @Test
    fun oldDaysAreForgottenOnceSentButKeptWhileWaitingForTheNetwork() {
        val old = today.minusDays(10)
        val older = today.minusDays(11)
        var log = UsageLog(memberId = "m1")
            .addSeconds(old, UsageScreen.Home, 60)
            .addSeconds(older, UsageScreen.Home, 60)
        log = log.markSent(old, log.toSend(today, 0)[old]!!, today, 0)
        assertEquals(setOf(older.toString()), log.days.keys)
        assertEquals(setOf(older), log.toSend(today, 0).keys)
    }
}
