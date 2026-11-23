package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.edit.nearestOccasionOf
import fr.familia.app.feature.gifts.surprise.surprisePickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * « Préparer une surprise » depuis un seul bouton (PRD rév. 287, récit 63): who is read for a
 * surprise, the choice of the person and the nearest occasion. The bar of the tab is checked in
 * [GiftsTabV2Test]. Fictional family only (TestFamily).
 */
class SurpriseButtonTest {

    private val today = LocalDate.of(2026, 11, 15)

    /** adult1 has a birthday in five days. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult1") it.copy(birthDate = LocalDate.of(1990, 11, 20)) else it },
    )

    @Test
    fun `with the button, every other member is read for a surprise idea`() {
        val state = giftsState(family, emptyList(), "adult2", today, surpriseButton = true) as GiftsUiState.Success
        assertEquals(setOf("admin1", "adult1", "kid1", "kid2"), surpriseIdeasToRead(state).toSet())
        val ideas = preparingLines(state.copy(surprisePreparedFor = setOf("adult1")))
        assertEquals(listOf("ideas-adult1"), ideas.map { it.key })
    }

    @Test
    fun `the person celebrated is never among the surprises in progress`() {
        val state = giftsState(family, emptyList(), "adult1", today, surpriseButton = true) as GiftsUiState.Success
        assertTrue("adult1" !in surpriseIdeasToRead(state))
        assertTrue(preparingLines(state.copy(surprisePreparedFor = setOf("adult1"))).isEmpty())
    }

    @Test
    fun `the choice of the person leaves me out, the next birthday first`() {
        val pick = surprisePickState(family, "adult2", today)
        assertEquals("adult1", pick.next?.member?.id)
        val everyone = pick.groups.flatMap { group -> group.members.map { it.id } }
        assertTrue("adult2" !in everyone)
        assertEquals(listOf("Foyer Deux", "Foyer Un"), pick.groups.map { it.name })
    }

    @Test
    fun `the nearest occasion is chosen`() {
        val adult1 = family.members.first { it.id == "adult1" }
        assertEquals(GiftOccasion.Birthday to LocalDate.of(2026, 11, 20), nearestOccasionOf(adult1, today))
        val later = adult1.copy(birthDate = LocalDate.of(1990, 2, 3))
        assertEquals(GiftOccasion.Christmas to LocalDate.of(2026, 12, 25), nearestOccasionOf(later, today))
        val none = adult1.copy(birthDate = null)
        assertEquals(GiftOccasion.Christmas to LocalDate.of(2027, 12, 25), nearestOccasionOf(none, LocalDate.of(2026, 12, 26)))
    }
}
