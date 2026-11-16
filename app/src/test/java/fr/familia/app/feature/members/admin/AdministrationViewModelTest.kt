package fr.familia.app.feature.members.admin

import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.ConsumptionRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.model.Consumption
import fr.familia.app.feature.capsules.FakeCapsules
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

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
    fun `the sealed capsules of a member who left are deleted once, on request (PRD rev 184)`() = runTest {
        val leftovers = MutableStateFlow(listOf(CapsuleLeftover("gone", "Jules", 2, deleteAsked = false)))
        val asked = mutableListOf<String>()
        val capsules = object : CapsuleRepository by FakeCapsules() {
            override fun observeLeftovers(): Flow<List<CapsuleLeftover>> = leftovers
            override suspend fun askDeleteLeftovers(memberId: String) {
                asked += memberId
                leftovers.value = leftovers.value.map { if (it.memberId == memberId) it.copy(deleteAsked = true) else it }
            }
        }
        val vm = AdministrationViewModel(FakeMembers(family), "o", capsules = capsules)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.capsuleLeftovers.collect {} }
        assertEquals(2, vm.capsuleLeftovers.value.single().count)
        vm.deleteCapsuleLeftovers("unknown")
        vm.deleteCapsuleLeftovers("gone")
        vm.deleteCapsuleLeftovers("gone")
        assertEquals(listOf("gone"), asked)
        assertTrue(vm.capsuleLeftovers.value.single().deleteAsked)
    }

    @Test
    fun `a refused read of the leftovers hides the card`() = runTest {
        val capsules = object : CapsuleRepository by FakeCapsules() {
            override fun observeLeftovers(): Flow<List<CapsuleLeftover>> = kotlinx.coroutines.flow.flow { throw IOException("refused") }
        }
        val vm = AdministrationViewModel(FakeMembers(family), "o", capsules = capsules)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.capsuleLeftovers.collect {} }
        assertTrue(vm.capsuleLeftovers.value.isEmpty())
    }

    @Test
    fun `the consumption card waits for the first morning, then shows the latest day`() = runTest {
        val latest = MutableStateFlow<Consumption?>(null)
        val vm = AdministrationViewModel(FakeMembers(family), "o", consumptionRepository = object : ConsumptionRepository {
            override fun observeLatest(): Flow<Consumption?> = latest
        })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.consumption.collect {} }
        assertEquals(ConsumptionCard.Waiting, vm.consumption.value)
        val day = Consumption(LocalDate.of(2026, 11, 20), reads = 3_000)
        latest.value = day
        assertEquals(ConsumptionCard.Ready(day), vm.consumption.value)
    }

    @Test
    fun `the admin principal picks the challenge of this month and the next, from the list only (récit 35)`() = runTest {
        val picks = mutableListOf<Pair<YearMonth, String>>()
        val picked = MutableStateFlow<String?>(null)
        val tree = object : TreeRepository {
            override fun observeTree(): Flow<FamilyTree> = flowOf(FamilyTree())
            override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = flowOf(null)
            override fun observePick(month: YearMonth): Flow<String?> = if (month == YearMonth.of(2027, 1)) picked else flowOf(null)
            override suspend fun pick(month: YearMonth, choiceId: String) {
                picks += month to choiceId
                picked.value = choiceId
            }
        }
        val vm = AdministrationViewModel(FakeMembers(family), "o", tree = tree, month = YearMonth.of(2026, 12))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.challenges.collect {} }
        assertEquals(listOf("christmasPhotos", "goodNewsStart"), vm.challenges.value.map { it.choice.id })
        vm.pickChallenge(YearMonth.of(2027, 1), "pets")
        vm.pickChallenge(YearMonth.of(2027, 1), "1000 photos")
        assertEquals(listOf(YearMonth.of(2027, 1) to "pets"), picks)
        assertEquals("pets", vm.challenges.value[1].choice.id)
    }

    @Test
    fun `a refused read hides the consumption card`() = runTest {
        val vm = AdministrationViewModel(FakeMembers(family), "o", consumptionRepository = object : ConsumptionRepository {
            override fun observeLatest(): Flow<Consumption?> = kotlinx.coroutines.flow.flow { throw IOException("refused") }
        })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.consumption.collect {} }
        assertNull(vm.consumption.value)
    }

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
