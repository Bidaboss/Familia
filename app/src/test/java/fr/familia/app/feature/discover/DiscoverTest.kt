package fr.familia.app.feature.discover

import fr.familia.app.core.model.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DiscoverTest {

    private val allOn: (Feature) -> Boolean = { true }
    private fun day(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `nothing before Monday 30 November, then the question of the week all that week`() {
        assertNull(Discover.moduleOfTheWeek(day(11, 29), allOn))
        assertEquals(DiscoverModule.WeeklyQuestion, Discover.moduleOfTheWeek(day(11, 30), allOn))
        assertEquals(DiscoverModule.WeeklyQuestion, Discover.moduleOfTheWeek(day(12, 6), allOn))
        assertEquals(DiscoverModule.HelpRequests, Discover.moduleOfTheWeek(day(12, 7), allOn))
    }

    @Test
    fun `a module of the tutorial is left out and the next one moves up`() {
        val tutorial = Discover.inTutorial(allOn)
        assertEquals(setOf(DiscoverModule.HelpRequests), tutorial)
        assertEquals(DiscoverModule.Polls, Discover.moduleOfTheWeek(day(12, 7), allOn, inTutorial = tutorial))
        assertEquals(DiscoverModule.Genealogy, Discover.moduleOfTheWeek(day(1, 4, 2027), allOn, inTutorial = tutorial))
        assertNull(Discover.moduleOfTheWeek(day(1, 11, 2027), allOn, inTutorial = tutorial))
    }

    @Test
    fun `opened, put off or switched off, the card stays away`() {
        assertNull(Discover.moduleOfTheWeek(day(11, 30), allOn, dismissed = setOf("weeklyQuestion")))
        assertNull(Discover.moduleOfTheWeek(day(11, 30), { it != Feature.WeeklyQuestion }))
        assertEquals(
            DiscoverModule.Recipes,
            Discover.moduleOfTheWeek(day(11, 30), allOn, dismissed = setOf("polls"), order = listOf(DiscoverModule.Recipes)),
        )
    }

    @Test
    fun `Entraide is in the tutorial only while its module is on`() {
        assertEquals(emptySet<DiscoverModule>(), Discover.inTutorial { it != Feature.HelpRequests })
    }
}
