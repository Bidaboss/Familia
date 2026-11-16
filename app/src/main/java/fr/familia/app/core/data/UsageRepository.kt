package fr.familia.app.core.data

import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.UsageDay
import java.time.LocalDate

/**
 * The time spent in the app, day by day (PRD, récit 1 nonies): usage/{memberId}/days/{day}.
 * Each member writes their own days; the admin principal reads everyone's, and the responsible
 * adults of a child read that child's (carte EXPERT-5).
 */
interface UsageRepository {
    /** Replaces the counts of [day]: the phone always sends the whole day. */
    suspend fun saveDay(memberId: String, day: LocalDate, usage: DayUsage)

    /** The days of [memberId] from [since] on, read once from the server. */
    suspend fun fetchDays(memberId: String, since: LocalDate): List<UsageDay>

    /** The last day [memberId] used the app, in the 13 months kept; null if never. */
    suspend fun fetchLastDay(memberId: String): LocalDate?
}
