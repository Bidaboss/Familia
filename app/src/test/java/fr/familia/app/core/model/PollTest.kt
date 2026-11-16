package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class PollTest {

    private fun poll(kind: Poll.Kind = Poll.Kind.Date, options: List<String> = listOf("2026-12-19", "2026-12-20 12:30"), closesAt: Instant? = null, closed: Boolean = false) =
        Poll("p", kind, "Quand ?", options, false, listOf("a", "b", "c"), listOf("a", "b", "c"), null, closesAt, closed, null, null, "a", "A", null)

    @Test
    fun `a date option reads with or without its time`() {
        assertEquals(LocalDate.of(2026, 12, 19) to null, Poll.dateOption("2026-12-19"))
        assertEquals(LocalDate.of(2026, 12, 20) to LocalTime.of(12, 30), Poll.dateOption("2026-12-20 12:30"))
        assertNull(Poll.dateOption("La Pizzeria"))
        assertEquals("2026-12-20 09:05", Poll.dateOptionKey(LocalDate.of(2026, 12, 20), LocalTime.of(9, 5)))
    }

    @Test
    fun `a poll is open until closed or its end`() {
        val end = Instant.parse("2026-12-01T23:00:00Z")
        assertTrue(poll(closesAt = end).isOpen(end.minusSeconds(1)))
        assertFalse(poll(closesAt = end).isOpen(end))
        assertFalse(poll(closed = true).isOpen(end))
        assertTrue(poll().isOpen(end))
    }

    @Test
    fun `the lead is the most yes, then the most maybe`() {
        val votes = listOf(
            PollVote("a", answers = listOf(DateAnswer.Yes, DateAnswer.Yes), votedBy = "a"),
            PollVote("b", answers = listOf(DateAnswer.Maybe, DateAnswer.Yes), votedBy = "b"),
            PollVote("c", answers = listOf(DateAnswer.Yes, DateAnswer.No), votedBy = "c"),
        )
        assertEquals(listOf("a", "c"), tally(poll(), votes)[0].yes)
        assertEquals(listOf("b"), tally(poll(), votes)[0].maybe)
        // Two yes each: the first date wins on its maybe.
        assertEquals(setOf(0), leaders(poll(), votes))
        assertEquals(listOf("c"), notAnswered(poll(), votes.take(2)))
    }

    @Test
    fun `nobody leads before the first answer, and ties all lead`() {
        val free = poll(Poll.Kind.Choice, listOf("Pizza", "Crêpes"))
        assertEquals(emptySet<Int>(), leaders(free, emptyList()))
        val votes = listOf(PollVote("a", choices = listOf(0), votedBy = "a"), PollVote("b", choices = listOf(1), votedBy = "b"))
        assertEquals(setOf(0, 1), leaders(free, votes))
    }

    @Test
    fun `the parents of a child asked see the poll`() {
        val adult = Member("p1", "P", null, null, "h", MemberProfile.Adult, false, emptyList(), null)
        val child = Member("k1", "K", null, null, "h", MemberProfile.ChildWithoutApp, false, listOf("p1", "p2"), null)
        assertEquals(listOf("author", "k1", "p1", "p2"), pollViewers("author", listOf("k1"), listOf(adult, child)))
        assertEquals(listOf("p1", "k1", "p2"), pollViewers("p1", listOf("p1", "k1"), listOf(adult, child)))
    }
}
