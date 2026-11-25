package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.notebook.FakeNotebook
import fr.familia.app.testing.MainDispatcherRule
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

/**
 * Onglet « Propositions » (brief §4, fiche C4): every wish is in, the average shows from 3 notes,
 * my ideas stay private until added. TestFamily: admin1 and adult1 in h1, adult2 in h2.
 */
class SurpriseProposalsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val surprise = Surprise("s1", "adult2", "Les 40 ans", SurpriseOccasion.Birthday, LocalDate.of(2027, 2, 3), listOf("adult1", "admin1"), "adult1", "Adult1")

    private fun item(id: String, title: String) = GiftItem(id, title, null, 20.0, null, "adult2", "Adult2", true)

    private fun idea(id: String, person: String = "adult2", status: GiftStatus = GiftStatus.Idea) =
        NotebookEntry(id, NotebookSource.Idea, person, "Adult2", NotebookOccasion(null, null), "Idée $id", null, 10.0, null, status)

    private fun state(
        proposals: List<SurpriseProposal> = emptyList(),
        ratings: Map<String, Int> = emptyMap(),
        entries: List<NotebookEntry> = emptyList(),
        me: String = "adult1",
    ) = surpriseStateForTest(
        surprise, TestFamily.family.members, listOf(item("w1", "Un vélo"), item("w2", "Un livre")), proposals, ratings, entries,
        listOf(GiftHistoryItem("h1", "Une montre", 2025, SurpriseOccasion.Christmas)), me,
    )!!

    @Test
    fun `every wish of the person is in the surprise, noted or not`() {
        val s = state(proposals = listOf(SurpriseProposal("wish-w1", ProposalKind.Wish, "Un vélo", 20.0, null, "adult2", "Adult2", ratingSum = 14, ratingCount = 3)))
        assertEquals(listOf("wish-w1", "wish-w2"), s.wishes.map { it.proposal.id })
        assertEquals(14.0 / 3, s.wishes.first().proposal.average!!, 0.001)
        assertNull(s.wishes.last().proposal.average)
        assertEquals(1, s.history.size)
    }

    @Test
    fun `under three notes, no average, so nobody guesses who gave which`() {
        val two = SurpriseProposal("wish-w1", ProposalKind.Wish, "Un vélo", 20.0, null, "adult2", "Adult2", ratingSum = 9, ratingCount = 2)
        assertNull(two.average)
    }

    @Test
    fun `my ideas for the person are private until added, the others' ideas are listed apart`() {
        val mine = SurpriseProposal("idea-e1", ProposalKind.Idea, "Idée e1", 10.0, null, "adult1", "Adult1")
        val theirs = SurpriseProposal("idea-x9", ProposalKind.Idea, "Un jeu", null, null, "admin1", "Admin1")
        val s = state(proposals = listOf(mine, theirs), entries = listOf(idea("e1"), idea("e2"), idea("e3", person = "admin1"), idea("e4", status = GiftStatus.Given)))
        assertEquals(listOf("e1" to true, "e2" to false), s.myIdeas.map { it.entry.id to it.added })
        assertEquals(listOf("idea-x9"), s.othersIdeas.map { it.proposal.id })
        assertEquals(4, s.proposalCount)
    }

    @Test
    fun `a note is mine alone, the same star again takes it back, an idea goes in and out`() = runTest {
        val repo = FakeSurprises(listOf(surprise))
        val notebook = FakeNotebook(listOf(idea("e1")))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, notebook, "adult1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val ready = vm.state.value as SurpriseUiState.Ready
        assertTrue(ready.wishes.isEmpty())
        val row = MyIdeaRow(ready.myIdeas.single().entry, added = false)
        vm.toggleIdea(row, "Adult1")
        val added = (vm.state.value as SurpriseUiState.Ready)
        assertTrue(added.myIdeas.single().added)
        assertTrue(added.othersIdeas.isEmpty())
        val proposalRow = ProposalRow(repo.proposals.value["s1"]!!.single(), null)
        vm.rate(proposalRow, 4)
        assertEquals(mapOf("idea-e1" to 4), repo.ratings.value["s1/adult1"])
        vm.rate(proposalRow.copy(myStars = 4), 4)
        assertEquals(emptyMap<String, Int>(), repo.ratings.value["s1/adult1"])
        vm.toggleIdea(row.copy(added = true), "Adult1")
        assertFalse((vm.state.value as SurpriseUiState.Ready).myIdeas.single().added)
    }

    @Test
    fun `someone not in the surprise does not see it`() = runTest {
        val vm = SurpriseViewModel(FakeSurprises(listOf(surprise)), FakeMembers(), null, FakeNotebook(), "adult2", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(SurpriseUiState.Unavailable, vm.state.value)
    }
}
