package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import fr.familia.app.core.model.jointGoal
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
 * Onglet « Résultats » (brief §4 and §6, fiches C5 and C6): ranked by average, ✕ to take out,
 * « Je le prends » and the joint gift, « Valider » only once I take part. TestFamily: admin1 and
 * adult1 in h1, adult2 in h2, kid1 a child with the app.
 */
class SurpriseResultsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val surprise = Surprise(
        "s1", "adult2", "Les 40 ans", SurpriseOccasion.Birthday, LocalDate.of(2027, 2, 3), listOf("adult1", "admin1", "kid1"), "adult1", "Adult1",
    )

    private fun item(id: String, title: String, price: Double = 20.0) = GiftItem(id, title, null, price, null, "adult2", "Adult2", true)

    private fun wish(id: String, title: String, sum: Int = 0, count: Int = 0, removed: Boolean = false) =
        SurpriseProposal("wish-$id", ProposalKind.Wish, title, 20.0, null, "adult2", "Adult2", removed, sum, count)

    private fun idea(id: String, title: String) = SurpriseProposal("idea-$id", ProposalKind.Idea, title, 20.0, null, "admin1", "Admin1")

    private val items = listOf(item("w1", "Un vélo"), item("w2", "Un livre"), item("w3", "Un casque"))

    private fun state(
        proposals: List<SurpriseProposal> = emptyList(),
        takes: List<SurpriseTake> = emptyList(),
        myShares: Map<String, Int> = emptyMap(),
        ownShares: Map<String, Map<String, Int>> = emptyMap(),
        rows: List<SurpriseMemberRow> = emptyList(),
        me: String = "adult1",
    ) = surpriseStateForTest(
        surprise, TestFamily.family.members, items, proposals, emptyMap(), emptyList(), emptyList(), me, takes, myShares, ownShares, rows,
    )!!

    @Test
    fun `ranked by average, the best one marked, the ones taken out apart`() {
        val idea = SurpriseProposal("idea-e1", ProposalKind.Idea, "Un carnet", 12.0, null, "admin1", "Admin1", ratingSum = 15, ratingCount = 3)
        val s = state(proposals = listOf(wish("w1", "Un vélo", 12, 3), wish("w2", "Un livre", removed = true), idea))
        assertEquals(listOf("idea-e1", "wish-w1", "wish-w3"), s.results.map { it.proposal.id })
        assertEquals(listOf(1, 2, 3), s.results.map { it.rank })
        assertTrue(s.results.first().best)
        // 3 notes of 5 among 3 members who take part: everyone agrees.
        assertTrue(s.results.first().unanimous)
        assertFalse(s.results[1].best)
        assertEquals(listOf("wish-w2"), s.removed.map { it.proposal.id })
    }

    @Test
    fun `a joint gift, the others read the total, its owner reads every part`() {
        val take = SurpriseTake("wish-w1", TakeKind.Shared, "admin1", goal = 49, sharesTotal = 30, sharesCount = 2)
        val mine = state(takes = listOf(take), myShares = mapOf("wish-w1" to 10))
        val row = mine.results.first { it.proposal.id == "wish-w1" }
        assertEquals("Admin1", row.ownerName)
        assertEquals(10, row.myShare)
        assertTrue(row.shares.isEmpty())
        assertEquals(19, take.missing)
        val owner = state(takes = listOf(take), ownShares = mapOf("wish-w1" to mapOf("adult1" to 10, "admin1" to 20)), me = "admin1")
        assertEquals(listOf("" to 20, "Adult1" to 10), owner.results.first { it.proposal.id == "wish-w1" }.shares)
        assertEquals(1, owner.takenCount)
    }

    @Test
    fun `validate only once I take part, and the validation goes when I take part in nothing`() {
        assertFalse(state().participates)
        val none = state(rows = listOf(SurpriseMemberRow("adult1", validated = true)))
        assertFalse(none.validated)
        assertEquals(MemberState.Waiting, none.statuses.first().state)
        val took = state(takes = listOf(SurpriseTake("wish-w2", TakeKind.Solo, "adult1")), rows = listOf(SurpriseMemberRow("adult1", validated = true)))
        assertTrue(took.validated)
        assertEquals("adult1", took.statuses.first().memberId)
        assertEquals(MemberState.Done, took.statuses.first().state)
        assertFalse(took.isReady)
        val all = state(
            takes = listOf(SurpriseTake("wish-w2", TakeKind.Solo, "adult1")),
            rows = listOf(SurpriseMemberRow("adult1", validated = true), SurpriseMemberRow("admin1", validated = true), SurpriseMemberRow("kid1", declined = true)),
        )
        assertTrue(all.isReady)
        assertEquals(MemberState.Declined, all.statuses.first { it.memberId == "kid1" }.state)
    }

    @Test
    fun `a child takes alone, never a joint gift`() {
        assertFalse(state(me = "kid1").isAdult)
        assertTrue(state().isAdult)
    }

    @Test
    fun `take, share, validate, then decline releases what I took`() = runTest {
        val repo = FakeSurprises(listOf(surprise))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, FakeNotebook(), "adult1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        fun ready() = vm.state.value as SurpriseUiState.Ready
        // No gift repository here, so no wishes: ideas added by admin1.
        repo.proposals.value = mapOf("s1" to listOf(idea("w1", "Un vélo"), idea("w2", "Un livre")))
        vm.takeAlone(ready().results.first { it.proposal.id == "idea-w1" })
        assertTrue(ready().results.first { it.proposal.id == "idea-w1" }.takenByMe)
        vm.takeTogether(ready().results.first { it.proposal.id == "idea-w2" })
        assertEquals(jointGoal(20.0), repo.takes.value["s1"]!!.first { it.proposalId == "idea-w2" }.goal)
        vm.setShare(ready().results.first { it.proposal.id == "idea-w2" }, 15)
        assertEquals(15, ready().results.first { it.proposal.id == "idea-w2" }.myShare)
        vm.setValidated(true)
        assertTrue(ready().validated)
        vm.decline(ready())
        assertTrue(ready().declined)
        assertTrue(repo.takes.value["s1"].orEmpty().isEmpty())
        assertNull(ready().results.firstOrNull { it.takenByMe })
    }

    @Test
    fun `the ✕ takes a proposal out of the list, and puts it back`() = runTest {
        val repo = FakeSurprises(listOf(surprise))
        repo.proposals.value = mapOf("s1" to listOf(idea("w1", "Un vélo")))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, FakeNotebook(), "adult1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        fun ready() = vm.state.value as SurpriseUiState.Ready
        vm.setRemoved(ready().results.single(), true)
        assertTrue(ready().results.isEmpty())
        vm.setRemoved(ready().removed.single(), false)
        assertEquals(1, ready().results.size)
    }
}
