package fr.familia.app.feature.tree.challenges

import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.feature.tree.ChallengeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ChallengeHomeTest {

    private val december = YearMonth.of(2026, 12)

    @Test
    fun `the card waits for the first challenge, then shows only a month with one`() {
        assertTrue(showsChallenge(null, YearMonth.of(2026, 11)))
        assertFalse(showsChallenge(null, YearMonth.of(2027, 1)))
        assertTrue(showsChallenge(MonthChallenge(december, "Sapins", "🎄", 4, 1, false), december))
    }

    @Test
    fun `the days left count today, the last day is 1, another month says nothing`() {
        assertEquals(31, challengeDaysLeft(december, LocalDate.of(2026, 12, 1)))
        assertEquals(22, challengeDaysLeft(december, LocalDate.of(2026, 12, 10)))
        assertEquals(1, challengeDaysLeft(december, LocalDate.of(2026, 12, 31)))
        assertNull(challengeDaysLeft(december, LocalDate.of(2027, 1, 1)))
        assertNull(challengeDaysLeft(december, LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `every challenge of the list says in one sentence what to share`() {
        ChallengeCatalog.choices.forEach { assertTrue(it.id, it.hint.endsWith(".") || it.hint.endsWith("…")) }
        assertEquals("Le sapin, les décorations, les préparatifs de Noël.", ChallengeCatalog.of("christmasPhotos")?.hint)
    }
}
