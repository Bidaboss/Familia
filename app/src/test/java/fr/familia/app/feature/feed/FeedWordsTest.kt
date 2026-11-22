package fr.familia.app.feature.feed

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The words of the steps in the fil (récit 88, 4/4). Fictional first names only. */
class FeedWordsTest {
    private fun m(id: String) = Member(id, id, null, null, "h1", MemberProfile.Adult, false, emptyList(), null)

    private val family = Family(listOf(Household("h1", "Les Martin")), listOf(m("ines"), m("noe"), m("sam")))
    private val today = LocalDate.of(2026, 11, 18)
    private val since = LocalDate.of(2026, 11, 4)

    private fun trip(id: String, readers: List<String>, start: LocalDate = today.minusDays(5), end: LocalDate = today.minusDays(2)) =
        Trip(id, "Voyage $id", start, end, listOf("ines"), readers, "ines", "ines")

    private fun step(id: String, place: String = "Ajaccio") =
        TripStep(id, StepType.Lodging, place, today.minusDays(4), null, null, null, null)

    private fun word(step: String, at: String?, author: String = "ines") =
        TripWord(step, "Bien arrivés", emptyList(), author, author, emptySet(), at?.let(Instant::parse))

    private val all = listOf("ines", "noe", "sam")

    @Test
    fun `a trip hidden from someone never reaches the fil, even for who follows it`() {
        val kept = feedWordTrips(listOf(trip("t1", all), trip("t2", listOf("ines", "noe"))), family, since, today)
        assertEquals(listOf("t1"), kept.map { it.id })
    }

    @Test
    fun `a trip not started yet or long over stays out`() {
        val trips = listOf(
            trip("soon", all, start = today.plusDays(3), end = today.plusDays(9)),
            trip("old", all, start = since.minusDays(60), end = since.minusDays(50)),
            trip("now", all),
        )
        assertEquals(listOf("now"), feedWordTrips(trips, family, since, today).map { it.id })
    }

    @Test
    fun `at most twenty trips are listened to`() {
        val trips = (1..30).map { trip("t$it", all, start = today.minusDays(it.toLong())) }
        assertEquals(FEED_WORDS_MAX, feedWordTrips(trips, family, since, today).size)
    }

    @Test
    fun `the words since the window on a step that exists, newest first`() {
        val trips = listOf(trip("t1", all))
        val cards = wordCards(
            trips,
            listOf(mapOf("s1" to word("s1", "2026-11-17T10:00:00Z"), "s2" to word("s2", "2026-11-18T08:00:00Z"), "old" to word("old", "2026-10-01T08:00:00Z"), "gone" to word("gone", "2026-11-18T09:00:00Z"), "wait" to word("wait", null))),
            listOf(listOf(step("s1"), step("s2", "Corte"), step("old"), step("wait"))),
            Instant.parse("2026-11-04T00:00:00Z"),
            10,
        )
        assertEquals(listOf("word:t1:s2", "word:t1:s1"), cards.map { it.key })
        assertEquals("Corte", cards.first().place)
    }

    @Test
    fun `the cards are cut at the limit`() {
        val words = (1..5).associate { "s$it" to word("s$it", "2026-11-1${it}T10:00:00Z") }
        val cards = wordCards(listOf(trip("t1", all)), listOf(words), listOf((1..5).map { step("s$it") }), Instant.parse("2026-11-04T00:00:00Z"), 3)
        assertEquals(3, cards.size)
    }
}
