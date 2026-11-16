package fr.familia.app.core.usage

import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.UsageScreen
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * What this phone counted and not yet sent, day by day (PRD, récit 1 nonies). Pure, so the rules
 * of when to send are tested without a phone: a finished day is sent once, today at most every
 * [TODAY_EVERY_MILLIS] so the admin sees a recent « dernière visite ».
 */
@Serializable
data class UsageLog(
    val memberId: String? = null,
    /** Day (AAAA-MM-JJ) → its counts. */
    val days: Map<String, Counts> = emptyMap(),
    /** Day → the counts last sent, so a day is sent again only when it changed. */
    val sent: Map<String, Counts> = emptyMap(),
    /** When today was last sent, in epoch milliseconds. */
    val todaySentAt: Long = 0,
    /** When the app last went to the background, to tell a new opening from a quick switch. */
    val stoppedAt: Long = 0,
) {
    @Serializable
    data class Counts(val opens: Int = 0, val seconds: Map<String, Int> = emptyMap()) {
        fun toDayUsage() = DayUsage(opens, seconds)
    }

    private fun counts(day: LocalDate) = days[day.toString()] ?: Counts()

    private fun with(day: LocalDate, usage: DayUsage) = copy(days = days + (day.toString() to Counts(usage.opens, usage.seconds)))

    /** The counts start over when another member signs in on this phone. */
    fun forMember(id: String): UsageLog = if (memberId == id) this else UsageLog(memberId = id)

    fun addSeconds(day: LocalDate, screen: UsageScreen, seconds: Int): UsageLog =
        if (seconds <= 0) this else with(day, counts(day).toDayUsage().withSeconds(screen, seconds))

    /** Counts an opening, unless the app was in the background for less than [REOPEN_AFTER_MILLIS]. */
    fun started(day: LocalDate, nowMillis: Long): UsageLog =
        if (stoppedAt != 0L && nowMillis - stoppedAt < REOPEN_AFTER_MILLIS) this else with(day, counts(day).toDayUsage().withOpen())

    fun stopped(nowMillis: Long): UsageLog = copy(stoppedAt = nowMillis)

    /** The days to send now: finished days that changed since sent, and today when due. */
    fun toSend(today: LocalDate, nowMillis: Long): Map<LocalDate, DayUsage> =
        days.mapNotNull { (key, c) ->
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
            val due = when {
                day.isAfter(today) -> false
                sent[key] == c -> false
                day == today -> nowMillis - todaySentAt >= TODAY_EVERY_MILLIS
                else -> true
            }
            if (due) day to c.toDayUsage() else null
        }.toMap()

    /**
     * Marks [day] as sent with [usage]. A day older than [KEEP_DAYS] is forgotten once what was
     * sent is all it holds; a day still waiting for the network stays.
     */
    fun markSent(day: LocalDate, usage: DayUsage, today: LocalDate, nowMillis: Long): UsageLog {
        val key = day.toString()
        val newSent = sent + (key to Counts(usage.opens, usage.seconds))
        val kept = days.filter { (k, c) -> keep(k, today) || newSent[k] != c }
        return copy(
            days = kept,
            sent = newSent.filterKeys { it in kept },
            todaySentAt = if (day == today) nowMillis else todaySentAt,
        )
    }

    private fun keep(key: String, today: LocalDate): Boolean =
        runCatching { !LocalDate.parse(key).isBefore(today.minusDays(KEEP_DAYS)) }.getOrDefault(false)

    companion object {
        const val REOPEN_AFTER_MILLIS = 5 * 60_000L
        const val TODAY_EVERY_MILLIS = 3 * 60 * 60_000L
        const val KEEP_DAYS = 7L
    }
}
