package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ChallengeRotationTest {
    private val december = YearMonth.of(2026, 12)
    private val martin = Household("h1", "Martin")
    private val bernard = Household("h2", "Bernard")
    private val petit = Household("h3", "Petit")

    @Test
    fun `the foyers take their turn a month each, like the server and the rules`() {
        val rotation = ChallengeRotation(listOf("h1", "h2"), december)
        assertNull(rotation.hostOf(YearMonth.of(2026, 11)))
        assertEquals("h1", rotation.hostOf(december))
        assertEquals("h2", rotation.hostOf(YearMonth.of(2027, 1)))
        assertEquals("h1", rotation.hostOf(YearMonth.of(2027, 2)))
        assertEquals("h2", rotation.hostOf(YearMonth.of(2028, 1)))
    }

    @Test
    fun `an order already set shows from the foyer of this month, and saving it changes nothing`() {
        val rotation = ChallengeRotation(listOf("h1", "h2", "h3"), YearMonth.of(2026, 10))
        val schedule = ChallengeRotation.schedule(rotation, listOf(martin, bernard, petit), december)
        assertEquals(december, schedule.start)
        assertEquals(listOf("h3", "h1", "h2"), schedule.households.map { it.id })
        assertTrue(schedule.isSaved)
        val again = ChallengeRotation(schedule.households.map { it.id }, schedule.start)
        for (i in 0L..6L) assertEquals(rotation.hostOf(december.plusMonths(i)), again.hostOf(december.plusMonths(i)))
    }

    @Test
    fun `a foyer deleted is left out and a new one comes last, to save again`() {
        val rotation = ChallengeRotation(listOf("h1", "gone"), december)
        val schedule = ChallengeRotation.schedule(rotation, listOf(martin, bernard), december)
        assertEquals(listOf("h1", "h2"), schedule.households.map { it.id })
        assertFalse(schedule.isSaved)
    }

    @Test
    fun `an order that starts later shows from its first month`() {
        val rotation = ChallengeRotation(listOf("h2", "h1"), YearMonth.of(2027, 1))
        val schedule = ChallengeRotation.schedule(rotation, listOf(martin, bernard), december)
        assertEquals(YearMonth.of(2027, 1), schedule.start)
        assertEquals(listOf("h2", "h1"), schedule.households.map { it.id })
    }
}
