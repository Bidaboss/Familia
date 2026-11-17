package fr.familia.app.feature.tree.challenges

import fr.familia.app.core.model.MonthChallenge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ChallengeHomeTest {

    private val december = YearMonth.of(2026, 12)

    @Test
    fun `the card waits for the first challenge, then shows only a month with one`() {
        assertTrue(showsChallenge(null, YearMonth.of(2026, 11)))
        assertFalse(showsChallenge(null, YearMonth.of(2027, 1)))
        assertTrue(showsChallenge(MonthChallenge(december, "Sapins", "🎄", 4, 1, false), december))
    }
}
