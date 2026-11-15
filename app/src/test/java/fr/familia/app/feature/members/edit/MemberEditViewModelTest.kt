package fr.familia.app.feature.members.edit

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class MemberEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val admin = Member("admin1", "Camille", "admin1@example.com", null, "h1", MemberProfile.Adult, true, emptyList(), "uid-a")
    private val adult = Member("adult1", "Sacha", "adult1@example.com", null, "h1", MemberProfile.Adult, false, emptyList(), "uid-b")
    // admin1 is the admin principal (and chef of h1).
    private val family = Family(listOf(Household("h1", "Foyer")), listOf(admin, adult), ownerId = "admin1")

    private fun vm(repo: FakeMembers, memberId: String? = null, me: Member = admin) =
        MemberEditViewModel(repo, me, memberId, today = { LocalDate.of(2026, 11, 14) })

    private fun MemberEditViewModel.editing() = uiState.value as MemberEditUiState.Editing

    @Test
    fun `a new member starts empty in the only household`() {
        val state = vm(FakeMembers(family)).editing()
        assertTrue(state.isNew)
        assertEquals("h1", state.form.householdId)
        assertEquals(listOf("Camille", "Sacha"), state.adults.map { it.firstName })
    }

    @Test
    fun `errors only show after a first save attempt, then follow the typing`() {
        val viewModel = vm(FakeMembers(family))
        viewModel.update { it.copy(firstName = "") }
        assertEquals(emptySet<FormError>(), viewModel.editing().errors)

        viewModel.save()
        assertTrue(FormError.FirstNameMissing in viewModel.editing().errors)
        viewModel.update { it.copy(firstName = "Sam") }
        assertFalse(FormError.FirstNameMissing in viewModel.editing().errors)
    }

    @Test
    fun `saving a new member writes it with its email and closes the form`() {
        val repo = FakeMembers(family)
        val viewModel = vm(repo)
        viewModel.update { it.copy(firstName = "Sam", email = "Sam@Example.com") }
        viewModel.save()
        assertEquals(MemberEditUiState.Added("Sam", "sam@example.com"), viewModel.uiState.value)
        val (saved, previous) = repo.saved.single()
        assertEquals("new1", saved.id)
        assertEquals("sam@example.com", saved.email)
        assertNull(saved.uid)
        assertNull(previous)
    }

    @Test
    fun `editing keeps the linked account and passes the old email`() {
        val repo = FakeMembers(family)
        val viewModel = vm(repo, memberId = "adult1")
        assertFalse(viewModel.editing().isNew)
        viewModel.update { it.copy(email = "new@example.com") }
        viewModel.save()
        val (saved, previous) = repo.saved.single()
        assertEquals("uid-b", saved.uid)
        assertEquals("adult1@example.com", previous)
    }

    @Test
    fun `the form never changes the chef flag, which lives in the Administration menu`() {
        val repo = FakeMembers(family)
        val viewModel = vm(repo, memberId = "admin1")
        viewModel.update { it.copy(isAdmin = false) }
        viewModel.save()
        assertTrue(repo.saved.single().first.isAdmin)
    }

    @Test
    fun `the form knows when it differs from what was opened`() {
        val viewModel = vm(FakeMembers(family), memberId = "adult1")
        assertFalse(viewModel.editing().hasChanges)
        viewModel.update { it.copy(firstName = "Sasha") }
        assertTrue(viewModel.editing().hasChanges)
        viewModel.update { it.copy(firstName = "Sacha") }
        assertFalse(viewModel.editing().hasChanges)
    }

    @Test
    fun `removing a parent keeps the child with the other responsible adult`() {
        val kid = Member("kid1", "Noé", null, null, "h1", MemberProfile.ChildWithoutApp, false, listOf("adult1", "admin1"), null)
        val repo = FakeMembers(family.copy(members = family.members + kid))
        val viewModel = vm(repo, memberId = "adult1")
        assertTrue(viewModel.editing().canRemove)
        assertEquals(emptyList<String>(), viewModel.editing().soleParentOf)

        viewModel.remove()
        assertEquals(MemberEditUiState.Saved, viewModel.uiState.value)
        val (removed, children) = repo.removed.single()
        assertEquals("adult1", removed.id)
        assertEquals(listOf("admin1"), children.single().parentIds)
    }

    @Test
    fun `the only responsible adult of a child cannot be removed`() {
        val kid = Member("kid1", "Noé", null, null, "h1", MemberProfile.ChildWithoutApp, false, listOf("adult1"), null)
        val repo = FakeMembers(family.copy(members = family.members + kid))
        val viewModel = vm(repo, memberId = "adult1")
        assertEquals(listOf("Noé"), viewModel.editing().soleParentOf)
        viewModel.remove()
        assertTrue(repo.removed.isEmpty())
    }

    @Test
    fun `nobody can remove themselves, and a new member has nothing to remove`() {
        assertFalse(vm(FakeMembers(family), memberId = "admin1").editing().canRemove)
        assertFalse(vm(FakeMembers(family)).editing().canRemove)
    }

    @Test
    fun `the invitation is offered until the member has signed in`() {
        val invited = adult.copy(id = "adult2", email = "adult2@example.com", uid = null)
        val repo = FakeMembers(family.copy(members = family.members + invited))
        assertTrue(vm(repo, memberId = "adult2").editing().canInvite)
        assertFalse(vm(repo, memberId = "adult1").editing().canInvite)
    }

    @Test
    fun `a member who is no longer chef cannot open the form`() {
        val demoted = admin.copy(isAdmin = false)
        val repo = FakeMembers(Family(family.households, listOf(demoted, adult)))
        assertEquals(MemberEditUiState.NotFound, vm(repo, me = admin).uiState.value)
    }

    @Test
    fun `a member that no longer exists shows the not found state`() {
        assertEquals(MemberEditUiState.NotFound, vm(FakeMembers(family), memberId = "gone").uiState.value)
    }

    @Test
    fun `a refused write keeps the form open with a message`() {
        val viewModel = vm(FakeMembers(family, failWith = IOException("denied")))
        viewModel.update { it.copy(firstName = "Sam", email = "sam@example.com") }
        viewModel.save()
        assertTrue(viewModel.editing().saveFailed)
        assertFalse(viewModel.editing().isSaving)
    }

    @Test
    fun `offline, a write still pending after the timeout closes the form`() = runTest {
        val viewModel = vm(FakeMembers(family, hang = true))
        viewModel.update { it.copy(firstName = "Sam", email = "sam@example.com") }
        viewModel.save()
        assertTrue(viewModel.editing().isSaving)
        advanceTimeBy(5_000)
        assertEquals(MemberEditUiState.Added("Sam", "sam@example.com"), viewModel.uiState.value)
    }

    @Test
    fun `a new household is created and selected`() {
        val repo = FakeMembers(family)
        val viewModel = vm(repo)
        viewModel.addHousehold("  Foyer Neuf ")
        assertEquals("h-new", viewModel.editing().form.householdId)
        assertEquals(listOf("Foyer", "Foyer Neuf"), viewModel.editing().households.map { it.name })
        assertEquals(Household("h-new", "Foyer Neuf"), repo.households.single())
    }

    private val chef = Member("chef2", "Inès", "chef2@example.com", null, "h2", MemberProfile.Adult, true, emptyList(), "uid-c")
    private val neighbour = Member("adult2", "Lou", "adult2@example.com", null, "h2", MemberProfile.Adult, false, emptyList(), "uid-d")
    private val twoHouseholds = Family(
        listOf(Household("h1", "Foyer"), Household("h2", "Voisins")),
        listOf(admin, adult, chef, neighbour),
        ownerId = "admin1",
    )

    @Test
    fun `a chef adds members to their own household only, without creating households`() {
        val state = vm(FakeMembers(twoHouseholds), me = chef).editing()
        assertEquals(listOf("h2"), state.households.map { it.id })
        assertEquals("h2", state.form.householdId)
        assertFalse(state.canAddHousehold)
        // Responsible adults come from the chef's household.
        assertEquals(listOf("Inès", "Lou"), state.adults.map { it.firstName })
    }

    @Test
    fun `a chef cannot open a member of another household, nor the admin principal`() {
        assertEquals(MemberEditUiState.NotFound, vm(FakeMembers(twoHouseholds), memberId = "adult1", me = chef).uiState.value)
        assertEquals(MemberEditUiState.NotFound, vm(FakeMembers(twoHouseholds), memberId = "admin1", me = chef).uiState.value)
        assertTrue(vm(FakeMembers(twoHouseholds), memberId = "adult2", me = chef).editing().canRemove)
    }

    @Test
    fun `only the admin principal removes a chef`() {
        val otherChef = neighbour.copy(isAdmin = true)
        val family = twoHouseholds.copy(members = listOf(admin, adult, chef, otherChef))
        val state = vm(FakeMembers(family), memberId = "adult2", me = chef).editing()
        assertFalse(state.canRemove)
        assertTrue(state.removeOwnerOnly)
        assertTrue(vm(FakeMembers(family), memberId = "adult2", me = admin).editing().canRemove)
    }

    @Test
    fun `a chef keeps the responsible adults the admin principal chose in another household`() {
        val kid = Member("kid2", "Noé", null, null, "h2", MemberProfile.ChildWithoutApp, false, listOf("adult1", "chef2"), null)
        val repo = FakeMembers(twoHouseholds.copy(members = twoHouseholds.members + kid))
        val viewModel = vm(repo, memberId = "kid2", me = chef)
        assertFalse(viewModel.editing().canEditParents)
        viewModel.update { it.copy(firstName = "Noa", parentIds = setOf("chef2")) }
        viewModel.save()
        assertEquals(listOf("adult1", "chef2"), repo.saved.single().first.parentIds)
    }

    @Test
    fun `a chef cannot create a household even if asked`() {
        val repo = FakeMembers(twoHouseholds)
        val viewModel = vm(repo, me = chef)
        viewModel.addHousehold("Nouveau")
        assertTrue(repo.households.isEmpty())
    }

    private class FakeMembers(
        private val family: Family,
        private val failWith: Exception? = null,
        private val hang: Boolean = false,
    ) : MemberRepository {
        val saved = mutableListOf<Pair<Member, String?>>()
        val households = mutableListOf<Household>()
        override suspend fun findByEmail(email: String): Member? = null
        override suspend fun linkAccount(memberId: String, uid: String) = Unit
        override fun observeFamily(): Flow<Family> = flowOf(family)
        override fun newMemberId(): String = "new1"
        override suspend fun saveMember(member: Member, previousEmail: String?) {
            failWith?.let { throw it }
            if (hang) awaitCancellation()
            saved += member to previousEmail
        }
        val removed = mutableListOf<Pair<Member, List<Member>>>()
        override suspend fun removeMember(member: Member, updatedChildren: List<Member>) {
            failWith?.let { throw it }
            removed += member to updatedChildren
        }
        override fun newHouseholdId(): String = "h-new"
        override suspend fun saveHousehold(household: Household) {
            households += household
        }
        override suspend fun deleteHousehold(householdId: String) = Unit
    }
}
