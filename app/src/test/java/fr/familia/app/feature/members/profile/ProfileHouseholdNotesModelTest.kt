package fr.familia.app.feature.members.profile

import fr.familia.app.core.data.MemberNoteRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Foyer » and « Mes notes » of the member page (règles 7 et 9). Fictional family only (TestFamily). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileHouseholdNotesModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class FakeNotes : MemberNoteRepository {
        val notes = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())
        val saves = mutableListOf<String>()
        override fun observe(authorId: String, aboutId: String): Flow<String> = notes.map { it[authorId to aboutId].orEmpty() }
        override suspend fun save(authorId: String, aboutId: String, text: String) {
            saves += text
            notes.value = notes.value + ((authorId to aboutId) to text)
        }
    }

    private class RecordingMembers(family: Family = TestFamily.family) : MemberRepository by FakeMembers(family) {
        val households = mutableListOf<Household>()
        override suspend fun saveHousehold(household: Household) {
            households += household
        }
    }

    /** adult2 is the chef of « Foyer Deux » here. */
    private val withChef = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult2") it.copy(isAdmin = true) else it },
    )

    private fun TestScope.model(
        reader: String,
        member: String,
        notes: MemberNoteRepository? = null,
        members: MemberRepository = RecordingMembers(withChef),
    ): MemberProfileV2ViewModel {
        val vm = MemberProfileV2ViewModel(members, null, null, null, null, reader, member, LocalDate.of(2026, 6, 10), notes = notes)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `a chef renames their household from their own page, nobody else but the admin`() = runTest {
        assertTrue(model("adult2", "adult2").state.value!!.canRenameHousehold)
        assertTrue(model("admin1", "adult2").state.value!!.canRenameHousehold)
        assertTrue(model("admin1", "admin1").state.value!!.canRenameHousehold)
        assertFalse(model("adult1", "adult1").state.value!!.canRenameHousehold)
        assertFalse(model("adult1", "adult2").state.value!!.canRenameHousehold)
        // The admin has no Foyer tile on every page, only on a chef's.
        assertFalse(model("admin1", "adult1").state.value!!.canRenameHousehold)
        assertEquals(2, model("adult2", "adult2").state.value!!.householdSize)
    }

    @Test
    fun `Renommer le foyer saves the trimmed name`() = runTest {
        val members = RecordingMembers(withChef)
        val vm = model("adult2", "adult2", members = members)
        vm.renameHousehold("  Les Deux  ")
        runCurrent()
        assertEquals(listOf(Household("h2", "Les Deux")), members.households)
    }

    @Test
    fun `nobody else renames the household, even by calling it`() = runTest {
        val members = RecordingMembers(withChef)
        val vm = model("adult1", "adult2", members = members)
        vm.renameHousehold("Autre nom")
        runCurrent()
        assertTrue(members.households.isEmpty())
    }

    @Test
    fun `my note about someone, saved once the typing pauses`() = runTest {
        val notes = FakeNotes()
        val vm = model("adult1", "adult2", notes)
        assertEquals("", vm.state.value!!.note)
        vm.editNote("Gui")
        vm.editNote("Guitare")
        advanceTimeBy(NOTE_PAUSE / 2)
        assertTrue(notes.saves.isEmpty())
        advanceTimeBy(NOTE_PAUSE)
        runCurrent()
        assertEquals(listOf("Guitare"), notes.saves)
        assertEquals("Guitare", vm.state.value!!.note)
    }

    @Test
    fun `a note stops at 500 characters`() = runTest {
        val notes = FakeNotes()
        val vm = model("adult1", "adult2", notes)
        vm.editNote("a".repeat(600))
        advanceTimeBy(NOTE_PAUSE + 1)
        runCurrent()
        assertEquals(NOTE_MAX, notes.saves.single().length)
    }

    @Test
    fun `no note on my own page, nor without the notes`() = runTest {
        val notes = FakeNotes()
        val mine = model("adult1", "adult1", notes)
        assertNull(mine.state.value!!.note)
        mine.editNote("Rien")
        advanceTimeBy(NOTE_PAUSE + 1)
        runCurrent()
        assertTrue(notes.saves.isEmpty())
        assertNull(model("adult1", "adult2", notes = null).state.value!!.note)
    }
}
