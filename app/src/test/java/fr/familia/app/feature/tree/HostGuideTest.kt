package fr.familia.app.feature.tree

import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.MonthChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HostGuideTest {
    private val december = YearMonth.of(2026, 12)
    private val january = december.plusMonths(1)
    private val rotation = ChallengeRotation(listOf("h1", "h2"), december)

    private fun hosted(month: YearMonth, status: HostedStatus) =
        HostedChallenge(month, "h1", status, HostedKind.Photo, "12 photos", "📸", HostedIllustration.Camera, 12)

    @Test
    fun `the foyer of the month prepares it while nobody has moved the gauge`() {
        val day = LocalDate.of(2026, 12, 3)
        assertEquals(december, HostGuide.turn(rotation, "h1", day, null, null, null))
        assertEquals(december, HostGuide.turn(rotation, "h1", day, MonthChallenge(december, "x", "", 10, 0, false), hosted(december, HostedStatus.Draft), null))
        assertNull(HostGuide.turn(rotation, "h1", day, MonthChallenge(december, "x", "", 10, 1, false), null, null))
        assertNull(HostGuide.turn(rotation, "h1", day, null, hosted(december, HostedStatus.Published), null))
        assertNull(HostGuide.turn(rotation, "h2", day, null, null, null))
    }

    @Test
    fun `the foyer of next month sees it from 7 days before, until it publishes or passes`() {
        assertNull(HostGuide.turn(rotation, "h2", LocalDate.of(2026, 12, 24), null, null, null))
        assertEquals(january, HostGuide.turn(rotation, "h2", LocalDate.of(2026, 12, 25), null, null, null))
        assertNull(HostGuide.turn(rotation, "h2", LocalDate.of(2026, 12, 25), null, null, hosted(january, HostedStatus.Skipped)))
        assertNull(HostGuide.turn(null, "h2", LocalDate.of(2026, 12, 25), null, null, null))
    }

    @Test
    fun `a target near the size of the family, and a start for each kind`() {
        assertEquals(13, HostGuide.suggestedTarget(13))
        assertEquals(5, HostGuide.suggestedTarget(2))
        assertEquals(30, HostGuide.suggestedTarget(80))
        assertEquals("13 photos de notre mois", HostGuide.start(HostedKind.Photo, 13).title)
        HostedKind.entries.forEach { kind ->
            val start = HostGuide.start(kind, 50)
            assert(start.title.length <= HostedChallenge.TITLE_MAX)
        }
    }
}
