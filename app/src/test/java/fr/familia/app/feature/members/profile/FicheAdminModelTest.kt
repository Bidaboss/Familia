package fr.familia.app.feature.members.profile

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.members.edit.FormError
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Modifier » of the admin principal, in place (règle 4, F9, F11). Fictional family only (TestFamily). */
@OptIn(ExperimentalCoroutinesApi::class)
class FicheAdminModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class Members(family: Family = TestFamily.family) : MemberRepository by FakeMembers(family) {
        val saved = mutableListOf<Member>()
        val removed = mutableListOf<Pair<Member, List<Member>>>()
        val households = mutableListOf<Household>()
        val nicknames = mutableListOf<String?>()
        override suspend fun saveNickname(memberId: String, nickname: String?) {
            nicknames += nickname
        }
        override suspend fun saveMember(member: Member, previousEmail: String?) {
            saved += member
        }
        override suspend fun removeMember(member: Member, updatedChildren: List<Member>) {
            removed += member to updatedChildren
        }
        override suspend fun saveHousehold(household: Household) {
            households += household
        }
    }

    private val withPhone = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult2") it.copy(phone = "06 12 34 56 78") else it },
        households = TestFamily.family.households.map { if (it.id == "h2") it.copy(address = "1 rue des Lilas") else it },
    )

    private fun TestScope.model(reader: String, member: String, members: MemberRepository = Members(withPhone)): MemberProfileV2ViewModel {
        val vm = MemberProfileV2ViewModel(members, null, null, null, null, reader, member, LocalDate.of(2026, 6, 10))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `the admin opens Modifier on every page, with Profil and Foyer`() = runTest {
        val vm = model("admin1", "adult2")
        vm.startEdit()
        val edit = vm.edit.value!!
        assertEquals("Adult2", edit.draft.form!!.firstName)
        assertEquals("1 rue des Lilas", edit.draft.address)
        assertFalse(edit.admin!!.isSelf)
        assertEquals(listOf("Kid2"), edit.admin!!.soleParentOf)
    }

    @Test
    fun `a responsible adult opens Modifier on their child's page, without Profil nor Foyer`() = runTest {
        val vm = model("adult1", "kid1")
        assertTrue(vm.state.value!!.canEdit)
        vm.startEdit()
        val edit = vm.edit.value!!
        assertNull(edit.draft.form)
        assertNull(edit.admin)
    }

    @Test
    fun `the chef of the household looks after its children, never another adult nor another household`() = runTest {
        val chef = TestFamily.family.copy(
            members = TestFamily.family.members.map {
                when (it.id) {
                    "adult2" -> it.copy(isAdmin = true)
                    "kid2" -> it.copy(parentIds = emptyList())
                    else -> it
                }
            },
        )
        assertTrue(model("adult2", "kid2", Members(chef)).state.value!!.canEdit)
        assertFalse(model("adult2", "kid1", Members(chef)).state.value!!.canEdit)
        assertFalse(model("adult1", "adult2").state.value!!.canEdit)
        assertFalse(model("adult1", "kid2").state.value!!.canEdit)
        val other = model("adult1", "adult2")
        other.startEdit()
        assertNull(other.edit.value)
    }

    @Test
    fun `a responsible adult saves the child's surnom, and nothing of the admin`() = runTest {
        val members = Members(withPhone)
        val vm = model("adult1", "kid1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(nickname = "Titi"))
        vm.saveEdit()
        runCurrent()
        assertEquals(listOf("Titi"), members.nicknames)
        assertTrue(members.saved.isEmpty())
        assertTrue(members.households.isEmpty())
        assertNull(vm.edit.value)
    }

    @Test
    fun `the admin saves the member in one write, their phone kept and the surnom with it`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "adult2", members)
        vm.startEdit()
        val start = vm.edit.value!!.draft
        vm.updateDraft(start.copy(nickname = "Papi", form = start.form!!.copy(firstName = " Adulte Deux ", householdId = "h1")))
        vm.saveEdit()
        runCurrent()
        val saved = members.saved.single()
        assertEquals("Adulte Deux", saved.firstName)
        assertEquals("h1", saved.householdId)
        assertEquals("Papi", saved.nickname)
        assertEquals("06 12 34 56 78", saved.phone)
        assertTrue(members.households.isEmpty())
        assertNull(vm.edit.value)
    }

    @Test
    fun `the admin changes the address of the household`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "adult2", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(address = "2 rue des Roses"))
        vm.saveEdit()
        runCurrent()
        assertEquals(listOf(Household("h2", "Foyer Deux", "2 rue des Roses")), members.households)
        assertTrue(members.saved.isEmpty())
    }

    @Test
    fun `an empty first name is shown in red and nothing is saved`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "adult2", members)
        vm.startEdit()
        assertTrue(vm.edit.value!!.admin!!.errors.isEmpty())
        vm.updateDraft(vm.edit.value!!.draft.let { it.copy(form = it.form!!.copy(firstName = " ")) })
        vm.saveEdit()
        runCurrent()
        assertTrue(members.saved.isEmpty())
        assertTrue(FormError.FirstNameMissing in vm.edit.value!!.admin!!.errors)
    }

    @Test
    fun `a member nobody else answers for is never removed`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "adult2", members)
        vm.startEdit()
        vm.removeMember()
        runCurrent()
        assertTrue(members.removed.isEmpty())
    }

    @Test
    fun `the admin removes a member, the page closes with their first name`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "kid1", members)
        vm.startEdit()
        vm.removeMember()
        runCurrent()
        assertEquals("kid1", members.removed.single().first.id)
        assertEquals("Kid1", vm.removed.value)
        assertNull(vm.edit.value)
    }

    @Test
    fun `the admin is never removed, even from their own page`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "admin1", members)
        vm.startEdit()
        assertTrue(vm.edit.value!!.admin!!.isSelf)
        vm.removeMember()
        runCurrent()
        assertTrue(members.removed.isEmpty())
    }

    @Test
    fun `a new household is created and chosen`() = runTest {
        val members = Members(withPhone)
        val vm = model("admin1", "adult2", members)
        vm.startEdit()
        vm.addHousehold("  Les Nouveaux ")
        runCurrent()
        assertEquals(listOf(Household("h-new", "Les Nouveaux")), members.households)
        assertEquals("h-new", vm.edit.value!!.draft.form!!.householdId)
    }
}
