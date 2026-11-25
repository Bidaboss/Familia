package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurprisesToConfirm
import fr.familia.app.core.model.surprisesToConfirm
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.notebook.FakeNotebook
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Le jour J (brief §6, fiche C7): the orange card, « C'est offert » once for all, the Accueil count. */
class SurpriseDayTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = LocalDate.of(2026, 12, 25)
    private val noel = Surprise("s1", "adult2", "Noël", SurpriseOccasion.Christmas, day, listOf("adult1", "admin1"), "adult1", "Adult1")
    private val later = Surprise("s2", "admin1", "Les 40 ans", SurpriseOccasion.Birthday, LocalDate.of(2027, 2, 3), listOf("adult1"), "adult1", "Adult1")

    private fun state(today: LocalDate) =
        surpriseStateForTest(noel, TestFamily.family.members, emptyList(), emptyList(), emptyMap(), emptyList(), emptyList(), "adult1", today = today)!!

    @Test
    fun `the orange card shows from the day itself, not before`() {
        assertFalse(state(day.minusDays(1)).isDay)
        assertTrue(state(day).isDay)
        assertTrue(state(day.plusDays(2)).isDay)
    }

    @Test
    fun `the Accueil counts the surprises waiting for « C'est offert »`() {
        assertNull(surprisesToConfirm(listOf(noel, later), day.minusDays(1)))
        assertEquals(SurprisesToConfirm(1, "Noël", "s1"), surprisesToConfirm(listOf(later, noel), day))
        assertEquals(2, surprisesToConfirm(listOf(noel, later), LocalDate.of(2027, 2, 3))?.count)
    }

    @Test
    fun `one member confirms for all, and the surprise is gone`() = runTest {
        val repo = FakeSurprises(listOf(noel))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, FakeNotebook(), "adult1", "s1", today = flowOf(day))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue((vm.state.value as SurpriseUiState.Ready).isDay)
        vm.markOffered()
        assertEquals(listOf("s1" to "adult1"), repo.offered)
        assertEquals(SurpriseUiState.Unavailable, vm.state.value)
    }
}
