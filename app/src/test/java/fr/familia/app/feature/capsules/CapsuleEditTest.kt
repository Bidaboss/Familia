package fr.familia.app.feature.capsules

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Nouvelle capsule » with fictional data of the test family (see TestFamily). */
@OptIn(ExperimentalCoroutinesApi::class)
class CapsuleEditTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 16)

    private fun vm(repo: FakeCapsules, saved: SavedStateHandle = SavedStateHandle()) =
        CapsuleEditViewModel(repo, FakeMembers(), "adult1", saved, { today })

    private fun editing(vm: CapsuleEditViewModel) = vm.uiState.value as CapsuleEditUiState.Editing

    @Test
    fun `the day must be between one month and five years from today`() {
        val form = CapsuleForm(text = "Coucou", recipientIds = setOf("kid1"))
        assertEquals(setOf(CapsuleFormError.DateMissing), validate(form, today))
        assertEquals(setOf(CapsuleFormError.DateOutOfRange), validate(form.copy(openAt = today.plusDays(20)), today))
        assertEquals(setOf(CapsuleFormError.DateOutOfRange), validate(form.copy(openAt = today.plusYears(5).plusDays(1)), today))
        assertTrue(validate(form.copy(openAt = today.plusMonths(1)), today).isEmpty())
        assertEquals(setOf(CapsuleFormError.TextMissing, CapsuleFormError.NoRecipient), validate(CapsuleForm(openAt = today.plusYears(1)), today))
    }

    @Test
    fun `a sealed capsule is signed, sorted and never opened yet`() = runTest {
        val repo = FakeCapsules()
        val vm = vm(repo)
        vm.setText("  Pour tes 18 ans  ")
        vm.toggleRecipient("kid1")
        vm.toggleRecipient("adult2")
        vm.setOpenAt(LocalDate.of(2031, 7, 19))

        vm.seal()

        assertEquals(CapsuleEditUiState.Done("k-new"), vm.uiState.value)
        val capsule = repo.capsules.value.single()
        assertEquals("Pour tes 18 ans", capsule.text)
        assertEquals(listOf("adult2", "kid1"), capsule.recipientIds)
        assertEquals("adult1", capsule.authorId)
        assertEquals(0, capsule.photoCount)
        assertTrue(capsule.openedFor.isEmpty())
    }

    @Test
    fun `nothing is written while the form is incomplete`() = runTest {
        val repo = FakeCapsules()
        val vm = vm(repo)
        vm.setText("Coucou")

        vm.seal()

        assertEquals(setOf(CapsuleFormError.NoRecipient, CapsuleFormError.DateMissing), editing(vm).errors)
        assertTrue(repo.capsules.value.isEmpty())
    }

    @Test
    fun `the draft survives Android stopping the app`() = runTest {
        val saved = SavedStateHandle()
        val first = vm(FakeCapsules(), saved = saved)
        first.setText("Un mot")
        first.toggleRecipient("adult2")
        first.setOpenAt(LocalDate.of(2027, 12, 24))

        val form = editing(vm(FakeCapsules(), saved = saved)).form

        assertEquals("Un mot", form.text)
        assertEquals(setOf("adult2"), form.recipientIds)
        assertEquals(LocalDate.of(2027, 12, 24), form.openAt)
    }
}
