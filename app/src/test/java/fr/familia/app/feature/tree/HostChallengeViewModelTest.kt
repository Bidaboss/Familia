package fr.familia.app.feature.tree

import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

private class FakeHosting(private val saved: HostedChallenge? = null) : TreeRepository {
    val writes = mutableListOf<Pair<HostedChallenge, String>>()
    override fun observeTree(): Flow<FamilyTree> = flowOf(FamilyTree())
    override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = flowOf(null)
    override fun observeHosted(month: YearMonth): Flow<HostedChallenge?> = flowOf(saved)
    override suspend fun saveHosted(challenge: HostedChallenge, memberId: String) {
        writes += challenge to memberId
    }
}

class HostChallengeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val january = YearMonth.of(2027, 1)
    private fun member(profile: MemberProfile) = Member("m1", "Camille", null, null, "h1", profile, false, emptyList(), null)

    @Test
    fun `the kind and the target write the title until someone types it`() = runTest {
        val vm = HostChallengeViewModel(FakeHosting(), null, member(MemberProfile.Adult), january)
        vm.chooseKind(HostedKind.Cooking)
        vm.setTarget(12)
        assertEquals("12 plats faits maison", vm.state.value.draft.title)
        assertEquals(HostedIllustration.Pot, vm.state.value.draft.illustration)
        vm.setTitle("Nos meilleurs gâteaux")
        vm.setTarget(99)
        vm.chooseKind(HostedKind.Photo)
        assertEquals("Nos meilleurs gâteaux", vm.state.value.draft.title)
        assertEquals(HostedChallenge.TARGET_MAX, vm.state.value.draft.target)
    }

    @Test
    fun `four steps, and no preview without a title`() = runTest {
        val vm = HostChallengeViewModel(FakeHosting(), null, member(MemberProfile.Adult), january)
        vm.next()
        vm.next()
        vm.setTitle("   ")
        vm.next()
        assertEquals(3, vm.state.value.step)
        vm.setTitle("Nos balades")
        vm.next()
        vm.next()
        assertEquals(4, vm.state.value.step)
        vm.back()
        assertEquals(3, vm.state.value.step)
    }

    @Test
    fun `an adult publishes or passes the turn, for the foyer`() = runTest {
        val repo = FakeHosting()
        val vm = HostChallengeViewModel(repo, null, member(MemberProfile.Adult), january)
        vm.publish()
        val (saved, by) = repo.writes.single()
        assertEquals(HostedStatus.Published, saved.status)
        assertEquals("h1", saved.householdId)
        assertEquals(january, saved.month)
        assertEquals("m1", by)
        assertEquals(HostedStatus.Published, vm.state.value.done)
    }

    @Test
    fun `the word of the foyer goes with the challenge, trimmed and within 140 characters`() = runTest {
        val repo = FakeHosting()
        val vm = HostChallengeViewModel(repo, null, member(MemberProfile.Adult), january)
        vm.setNote("x".repeat(200))
        assertEquals(HostedChallenge.NOTE_MAX, vm.state.value.draft.note.length)
        vm.setNote("  Vos plus beaux gâteaux  ")
        vm.publish()
        assertEquals("Vos plus beaux gâteaux", repo.writes.single().first.note)
    }

    @Test
    fun `a child saves the draft for the adults, and cannot publish`() = runTest {
        val repo = FakeHosting()
        val vm = HostChallengeViewModel(repo, null, member(MemberProfile.ChildWithApp), january)
        assertFalse(vm.state.value.canPublish)
        vm.publish()
        vm.skip()
        assertTrue(repo.writes.isEmpty())
        vm.saveDraft()
        assertEquals(HostedStatus.Draft, repo.writes.single().first.status)
    }

    @Test
    fun `the draft of the foyer is picked up where it was left`() = runTest {
        val draft = HostedChallenge(january, "h1", HostedStatus.Draft, HostedKind.Kindness, "8 gestes gentils", "💛", HostedIllustration.Heart, 8)
        val vm = HostChallengeViewModel(FakeHosting(draft), null, member(MemberProfile.Adult), january)
        assertEquals("8 gestes gentils", vm.state.value.draft.title)
        assertEquals(HostedKind.Kindness, vm.state.value.draft.kind)
        assertNull(vm.state.value.done)
    }
}
