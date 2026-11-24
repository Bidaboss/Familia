package fr.familia.app.feature.feed

import fr.familia.app.core.data.TripFilm
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The films of the day in the fil (récit 108, PRD 156). Fictional first names only. */
class FeedFilmsTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val trip = Trip("t1", "Corse", day, day.plusDays(3), listOf("ines"), listOf("ines", "sam"), "ines", "ines")
    private val steps = listOf(TripStep("s1", StepType.Lodging, "Ajaccio", day, null, null, null, null))
    private fun film(step: String, at: String?, d: Int = 14) =
        TripFilm("${step}_202611$d", "ines", "Ines", step, LocalDate.of(2026, 11, d), 10, 4, at?.let(Instant::parse))

    @Test
    fun `a card per film of a step still there, newest first, a film without server time waits`() {
        val films = listOf(
            film("s1", "2026-11-14T20:00:00Z"),
            film("s1", "2026-11-15T20:00:00Z", 15),
            film("gone", "2026-11-15T21:00:00Z"),
            film("s1", null, 16),
            film("s1", "2026-11-01T20:00:00Z", 13),
        )
        val cards = filmCards(listOf(trip), listOf(films), listOf(steps), Instant.parse("2026-11-10T00:00:00Z"), 10)
        assertEquals(listOf("film:t1:s1_20261115", "film:t1:s1_20261114"), cards.map { it.key })
        assertEquals("Ajaccio", cards.first().place)
    }
}
