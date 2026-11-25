package fr.familia.app.feature.members

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
class MembersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun m(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, household, profile, false, emptyList(), null)

    @Test
    fun `households are sorted by name, adults before children, then by first name`() {
        val family = Family(
            households = listOf(Household("h2", "Zèbre"), Household("h1", "Abeille")),
            members = listOf(
                m("1", "Zoé", "h1", MemberProfile.ChildWithoutApp),
                m("2", "bruno", "h1"),
                m("3", "Anna", "h1", MemberProfile.ChildWithApp),
                m("4", "Alice", "h1"),
                m("5", "Yann", "h2"),
            ),
        )
        val groups = MembersViewModel.group(family)
        assertEquals(listOf("Abeille", "Zèbre"), groups.map { it.household?.name })
        assertEquals(listOf("Alice", "bruno", "Anna", "Zoé"), groups[0].members.map { it.firstName })
    }

    @Test
    fun `an empty household is not shown`() {
        val family = Family(listOf(Household("h1", "Vide")), emptyList())
        assertEquals(emptyList<HouseholdGroup>(), MembersViewModel.group(family))
    }

    @Test
    fun `the screen follows live changes of the family`() = runTest {
        val source = MutableStateFlow(Family(listOf(Household("h1", "Foyer")), listOf(m("1", "Alice", "h1"))))
        val vm = MembersViewModel(FakeMembers(source), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, (vm.uiState.value as MembersUiState.Success).groups.single().members.size)

        source.value = source.value.copy(members = source.value.members + m("2", "Bruno", "h1"))
        assertEquals(2, (vm.uiState.value as MembersUiState.Success).groups.single().members.size)
    }

    @Test
    fun `the chef role is read live from the family`() = runTest {
        val me = m("1", "Alice", "h1")
        val neighbour = m("2", "Bruno", "h2")
        val households = listOf(Household("h1", "Foyer"), Household("h2", "Autre"))
        val source = MutableStateFlow(Family(households, listOf(me.copy(isAdmin = true), neighbour)))
        val vm = MembersViewModel(FakeMembers(source), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val chef = vm.uiState.value as MembersUiState.Success
        assertFalse(chef.canAddMembers)
        assertFalse(chef.isOwner)
        assertEquals(setOf("1"), chef.editableMemberIds)
        assertEquals(setOf("h1"), chef.renamableHouseholdIds)

        source.value = source.value.copy(members = listOf(me, neighbour))
        val plain = vm.uiState.value as MembersUiState.Success
        assertFalse(plain.canAddMembers)
        assertEquals(emptySet<String>(), plain.editableMemberIds)
    }

    @Test
    fun `the admin principal manages every member and household`() = runTest {
        val me = m("1", "Alice", "h1")
        val neighbour = m("2", "Bruno", "h2")
        val households = listOf(Household("h1", "Foyer"), Household("h2", "Autre"))
        val source = MutableStateFlow(Family(households, listOf(me, neighbour), ownerId = "1"))
        val vm = MembersViewModel(FakeMembers(source), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as MembersUiState.Success
        assertTrue(state.isOwner)
        assertEquals("1", state.ownerId)
        assertEquals(setOf("1", "2"), state.editableMemberIds)
        assertEquals(setOf("h1", "h2"), state.renamableHouseholdIds)
    }

    @Test
    fun `a failure shows the error state`() = runTest {
        val vm = MembersViewModel(FakeMembers(flow { throw IOException("offline") }), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(MembersUiState.Error, vm.uiState.value)
    }

    @Test
    fun `a member whose household is missing goes to the last group instead of disappearing`() {
        val family = Family(
            households = listOf(Household("h1", "Foyer")),
            members = listOf(m("1", "Alice", "h1"), m("2", "Bruno", "gone")),
        )
        val groups = MembersViewModel.group(family)
        assertEquals(listOf("Foyer", null), groups.map { it.household?.name })
        assertEquals(listOf("Bruno"), groups.last().members.map { it.firstName })
    }

    @Test
    fun `retry listens again after a failure`() = runTest {
        var calls = 0
        val repo = object : MemberRepository by FakeMembers(emptyFlow()) {
            override fun observeFamily(): Flow<Family> = flow {
                calls++
                if (calls == 1) throw IOException("denied")
                emit(Family(listOf(Household("h1", "Foyer")), listOf(m("1", "Alice", "h1"))))
            }
        }
        val vm = MembersViewModel(repo, currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(MembersUiState.Error, vm.uiState.value)

        vm.retry()
        assertEquals(2, calls)
        assertEquals("Alice", (vm.uiState.value as MembersUiState.Success).groups.single().members.single().firstName)
    }

    @Test
    fun `renaming a household trims the name and saves it`() = runTest {
        val saved = mutableListOf<Household>()
        val repo = object : MemberRepository by FakeMembers(emptyFlow()) {
            override suspend fun saveHousehold(household: Household) { saved += household }
        }
        val vm = MembersViewModel(repo, currentMemberId = "1")
        vm.renameHousehold(Household("h1", "Foyer"), "  Les Martin  ")
        assertEquals(listOf(Household("h1", "Les Martin")), saved)
        assertFalse(vm.renameFailed.value)
    }

    @Test
    fun `a blank or unchanged name saves nothing`() = runTest {
        val saved = mutableListOf<Household>()
        val repo = object : MemberRepository by FakeMembers(emptyFlow()) {
            override suspend fun saveHousehold(household: Household) { saved += household }
        }
        val vm = MembersViewModel(repo, currentMemberId = "1")
        vm.renameHousehold(Household("h1", "Foyer"), "   ")
        vm.renameHousehold(Household("h1", "Foyer"), " Foyer ")
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a name longer than the limit is cut`() = runTest {
        val saved = mutableListOf<Household>()
        val repo = object : MemberRepository by FakeMembers(emptyFlow()) {
            override suspend fun saveHousehold(household: Household) { saved += household }
        }
        val vm = MembersViewModel(repo, currentMemberId = "1")
        vm.renameHousehold(Household("h1", "Foyer"), "a".repeat(60))
        assertEquals(MembersViewModel.MAX_HOUSEHOLD_NAME, saved.single().name.length)
    }

    @Test
    fun `a refused rename is reported once`() = runTest {
        val repo = object : MemberRepository by FakeMembers(emptyFlow()) {
            override suspend fun saveHousehold(household: Household) { throw IOException("denied") }
        }
        val vm = MembersViewModel(repo, currentMemberId = "1")
        vm.renameHousehold(Household("h1", "Foyer"), "Les Martin")
        assertTrue(vm.renameFailed.value)
        vm.renameFailureShown()
        assertFalse(vm.renameFailed.value)
    }

    private class FakeMembers(private val family: Flow<Family>) : MemberRepository {
        override suspend fun findByEmail(email: String): Member? = null
        override suspend fun linkAccount(memberId: String, uid: String) = Unit
        override fun observeFamily(): Flow<Family> = family
        override fun newMemberId(): String = "new"
        override suspend fun saveMember(member: Member, previousEmail: String?) = Unit
        override suspend fun removeMember(member: Member, updatedChildren: List<Member>) = Unit
        override fun newHouseholdId(): String = "h-new"
        override suspend fun saveHousehold(household: Household) = Unit
        override suspend fun deleteHousehold(householdId: String) = Unit
    }
}
