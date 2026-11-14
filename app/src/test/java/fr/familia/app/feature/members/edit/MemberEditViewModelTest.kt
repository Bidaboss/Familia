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
    private val family = Family(listOf(Household("h1", "Foyer")), listOf(admin, adult))

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
        assertEquals(MemberEditUiState.Saved, viewModel.uiState.value)
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
    fun `an admin cannot change their own admin flag`() {
        val repo = FakeMembers(family)
        val viewModel = vm(repo, memberId = "admin1")
        assertFalse(viewModel.editing().canChangeAdmin)
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
        assertEquals(MemberEditUiState.Saved, viewModel.uiState.value)
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
        override fun newHouseholdId(): String = "h-new"
        override suspend fun saveHousehold(household: Household) {
            households += household
        }
    }
}
