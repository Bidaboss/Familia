package fr.familia.app.feature.members.admin

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AdministrationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun m(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult, chef: Boolean = false) =
        Member(id, name, "$id@example.com", null, household, profile, chef, emptyList(), null)

    private val owner = m("o", "Camille", "h1")
    private val adult = m("a", "Sacha", "h2")
    private val kid = m("k", "Noé", "h2", MemberProfile.ChildWithApp)
    private val family = Family(
        listOf(Household("h1", "Martin"), Household("h2", "Bernard"), Household("h3", "Vide")),
        listOf(owner, adult, kid),
        ownerId = "o",
    )

    private fun adminVm(repo: FakeMembers, me: String = "o") = AdministrationViewModel(repo, me)

    @Test
    fun `only the admin principal opens the menu`() {
        assertEquals(AdministrationUiState.NotAllowed, AdministrationViewModel.state(family, "a"))
        assertEquals(AdministrationUiState.NotAllowed, AdministrationViewModel.state(family.copy(ownerId = null), "o"))
    }

    @Test
    fun `adults only can be chefs, and households show how many members they have`() {
        val state = AdministrationViewModel.state(family, "o") as AdministrationUiState.Ready
        assertEquals(listOf("Camille", "Sacha"), state.adults.map { it.member.firstName })
        assertTrue(state.adults.first().isOwner)
        assertEquals(listOf("Bernard" to 2, "Martin" to 1, "Vide" to 0), state.households.map { it.household.name to it.memberCount })
    }

    @Test
    fun `naming a chef saves the member with the flag`() = runTest {
        val repo = FakeMembers(family)
        val vm = adminVm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setChef(adult, true)
        vm.setChef(kid, true)
        assertEquals(listOf(adult.copy(isAdmin = true) to adult.email), repo.saved)
    }

    @Test
    fun `only an empty household is deleted`() = runTest {
        val repo = FakeMembers(family)
        val vm = adminVm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.deleteHousehold(Household("h2", "Bernard"))
        vm.deleteHousehold(Household("h3", "Vide"))
        assertEquals(listOf("h3"), repo.deleted)
    }

    @Test
    fun `a new household is trimmed and saved`() = runTest {
        val repo = FakeMembers(family)
        val vm = adminVm(repo)
        vm.addHousehold("   ")
        vm.addHousehold("  Petit  ")
        assertEquals(listOf(Household("h-new", "Petit")), repo.households)
    }

    @Test
    fun `a refused change is reported once`() = runTest {
        val repo = FakeMembers(family, failWith = IOException("denied"))
        val vm = adminVm(repo)
        vm.setChef(adult, true)
        assertTrue(vm.actionFailed.value)
        vm.failureShown()
        assertFalse(vm.actionFailed.value)
    }

    private class FakeMembers(family: Family, private val failWith: Exception? = null) : MemberRepository {
        private val source = MutableStateFlow(family)
        val saved = mutableListOf<Pair<Member, String?>>()
        val households = mutableListOf<Household>()
        val deleted = mutableListOf<String>()
        override suspend fun findByEmail(email: String): Member? = null
        override suspend fun linkAccount(memberId: String, uid: String) = Unit
        override fun observeFamily(): Flow<Family> = source
        override fun newMemberId(): String = "new"
        override suspend fun saveMember(member: Member, previousEmail: String?) {
            failWith?.let { throw it }
            saved += member to previousEmail
        }
        override suspend fun removeMember(member: Member, updatedChildren: List<Member>) = Unit
        override fun newHouseholdId(): String = "h-new"
        override suspend fun saveHousehold(household: Household) {
            households += household
        }
        override suspend fun deleteHousehold(householdId: String) {
            deleted += householdId
        }
    }
}
