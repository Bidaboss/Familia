package fr.familia.app.feature.capsules

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.goodnews.FakeUploader
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

    private fun vm(
        repo: FakeCapsules,
        uploader: PhotoUploader? = null,
        online: Boolean = true,
        saved: SavedStateHandle = SavedStateHandle(),
        storage: PhotoStorage? = null,
    ) = CapsuleEditViewModel(repo, FakeMembers(), uploader, "adult1", saved, flowOf(online), { today }, storage = storage)

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

    @Test
    fun `the photos go to the author's folder before the capsule is sealed`() = runTest {
        val repo = FakeCapsules()
        val uploader = object : PhotoUploader {
            val sent = mutableListOf<String>()
            override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) {
                assertTrue(repo.capsules.value.isEmpty())
                sent += photoPath
            }
        }
        val vm = vm(repo, uploader)
        vm.setText("Pour tes 18 ans")
        vm.toggleRecipient("kid1")
        vm.setOpenAt(LocalDate.of(2031, 7, 19))
        vm.addPhotos(listOf("content://1", "content://2", "content://3", "content://4"))

        vm.seal()

        assertEquals(CapsuleEditUiState.Done("k-new"), vm.uiState.value)
        assertEquals(listOf("capsules/adult1/k-new/1.jpg", "capsules/adult1/k-new/2.jpg", "capsules/adult1/k-new/3.jpg"), uploader.sent)
        assertEquals(3, repo.capsules.value.single().photoCount)
    }

    @Test
    fun `offline, a capsule with photos waits, and nothing is sent`() = runTest {
        val repo = FakeCapsules()
        val uploader = FakeUploader()
        val vm = vm(repo, uploader, online = false)
        vm.setText("Coucou")
        vm.toggleRecipient("kid1")
        vm.setOpenAt(today.plusYears(1))
        vm.addPhotos(listOf("content://1"))

        vm.seal()

        assertEquals(CapsuleSaveFailure.NeedsNetwork, editing(vm).failure)
        assertTrue(uploader.sent.isEmpty())
        assertTrue(repo.capsules.value.isEmpty())
    }

    @Test
    fun `a photo that cannot be read leaves nothing behind`() = runTest {
        val repo = FakeCapsules()
        val removed = mutableListOf<String>()
        val storage = object : PhotoStorage {
            override suspend fun upload(path: String, jpeg: ByteArray) = Unit
            override suspend fun downloadUrl(path: String): String = path
            override suspend fun delete(path: String) {
                removed += path
            }
        }
        val vm = vm(repo, FakeUploader(unreadable = true), storage = storage)
        vm.setText("Coucou")
        vm.toggleRecipient("kid1")
        vm.setOpenAt(today.plusYears(1))
        vm.addPhotos(listOf("content://1"))

        vm.seal()

        assertEquals(CapsuleSaveFailure.PhotoUnreadable, editing(vm).failure)
        assertEquals(listOf("capsules/adult1/k-new/1.jpg", "capsules/adult1/k-new/1-thumb.jpg"), removed)
        assertTrue(repo.capsules.value.isEmpty())
    }
}
