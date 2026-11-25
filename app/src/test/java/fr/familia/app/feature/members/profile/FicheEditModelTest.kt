package fr.familia.app.feature.members.profile

import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberCard
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

/** « Modifier » in place on my own page (règle 3, F10). Fictional family only (TestFamily). */
@OptIn(ExperimentalCoroutinesApi::class)
class FicheEditModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class Members(family: Family = TestFamily.family) : MemberRepository by FakeMembers(family) {
        val nicknames = mutableListOf<String?>()
        val phones = mutableListOf<String?>()
        var refuse = false
        override suspend fun saveNickname(memberId: String, nickname: String?) {
            if (refuse) error("refused")
            nicknames += nickname
        }
        override suspend fun savePhone(memberId: String, phone: String?) {
            phones += phone
        }
    }

    private class Cards(card: MemberCard? = null) : MemberCardRepository {
        val card = MutableStateFlow(card)
        val saved = mutableListOf<MemberCard>()
        override fun observeCard(memberId: String): Flow<MemberCard?> = card.map { it?.takeIf { c -> c.memberId == memberId } }
        override suspend fun saveCard(card: MemberCard, byId: String) {
            saved += card
            this.card.value = card
        }
    }

    private fun TestScope.model(reader: String, member: String, members: MemberRepository = Members(), cards: MemberCardRepository? = Cards()): MemberProfileV2ViewModel {
        val vm = MemberProfileV2ViewModel(members, null, null, null, null, reader, member, LocalDate.of(2026, 6, 10), memberCards = cards)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `Modifier opens on my own page only`() = runTest {
        val mine = model("adult1", "adult1")
        mine.startEdit()
        assertEquals(FicheDraft("", "", MemberCard("adult1")), mine.edit.value!!.draft)
        assertFalse(mine.edit.value!!.canSave)

        val theirs = model("adult1", "adult2")
        theirs.startEdit()
        assertNull(theirs.edit.value)
    }

    @Test
    fun `only what changed is saved, then the page goes back to reading`() = runTest {
        val members = Members()
        val cards = Cards(MemberCard("adult1", allergies = "Arachides"))
        val vm = model("adult1", "adult1", members, cards)
        vm.startEdit()
        val start = vm.edit.value!!.draft
        vm.updateDraft(start.copy(nickname = "  Doudou ", card = start.card.copy(tastes = "Le thé vert")))
        assertTrue(vm.edit.value!!.canSave)
        vm.saveEdit()
        runCurrent()
        assertEquals(listOf<String?>("Doudou"), members.nicknames)
        assertTrue(members.phones.isEmpty())
        assertEquals(listOf(MemberCard("adult1", allergies = "Arachides", tastes = "Le thé vert")), cards.saved)
        assertNull(vm.edit.value)
    }

    @Test
    fun `a phone that is not one keeps Enregistrer off and saves nothing`() = runTest {
        val members = Members()
        val vm = model("adult1", "adult1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(phone = "appelle-moi"))
        assertFalse(vm.edit.value!!.phoneValid)
        assertFalse(vm.edit.value!!.canSave)
        vm.saveEdit()
        runCurrent()
        assertTrue(members.phones.isEmpty())
        assertTrue(vm.edit.value != null)
    }

    @Test
    fun `an emptied surnom and phone are removed`() = runTest {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.map { if (it.id == "adult1") it.copy(nickname = "Mamie", phone = "06 12 34 56 78") else it },
        )
        val members = Members(family)
        val vm = model("adult1", "adult1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(nickname = " ", phone = ""))
        vm.saveEdit()
        runCurrent()
        assertEquals(listOf<String?>(null), members.nicknames)
        assertEquals(listOf<String?>(null), members.phones)
    }

    @Test
    fun `a child never saves a phone`() = runTest {
        val members = Members()
        val vm = model("kid1", "kid1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(nickname = "Titi", phone = "06 12 34 56 78"))
        vm.saveEdit()
        runCurrent()
        assertEquals(listOf<String?>("Titi"), members.nicknames)
        assertTrue(members.phones.isEmpty())
    }

    @Test
    fun `a refusal keeps the fields open and says so`() = runTest {
        val members = Members().apply { refuse = true }
        val vm = model("adult1", "adult1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(nickname = "Doudou"))
        vm.saveEdit()
        runCurrent()
        assertEquals("Doudou", vm.edit.value!!.draft.nickname)
        assertTrue(vm.state.value!!.saveFailed)
    }

    @Test
    fun `Annuler drops what was typed`() = runTest {
        val members = Members()
        val vm = model("adult1", "adult1", members)
        vm.startEdit()
        vm.updateDraft(vm.edit.value!!.draft.copy(nickname = "x".repeat(40)))
        assertEquals(24, vm.edit.value!!.draft.nickname.length)
        vm.cancelEdit()
        runCurrent()
        assertNull(vm.edit.value)
        assertTrue(members.nicknames.isEmpty())
    }
}
