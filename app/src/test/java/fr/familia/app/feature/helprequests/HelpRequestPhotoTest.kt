package fr.familia.app.feature.helprequests

import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.helprequests.edit.HelpRequestEditUiState
import fr.familia.app.feature.helprequests.edit.HelpRequestEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** A photo joined to a coup de main (l'admin 16/11): a QR code, a parcel, a place. */
class HelpRequestPhotoTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 25)

    private class FakeStorage : PhotoStorage {
        val deleted = mutableListOf<String>()
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String) = "https://example.com/$path"
        override suspend fun delete(path: String) {
            deleted += path
        }
    }

    private val sent = mutableListOf<String>()
    private var failUpload = false
    private val uploader = PhotoUploader { _, photo, _ ->
        if (failUpload) throw IllegalStateException("offline")
        sent += photo
    }
    private val storage = FakeStorage()

    private fun edit(repo: FakeHelpRequests, me: String = "adult1", requestId: String? = null) =
        HelpRequestEditViewModel(repo, FakeMembers(), me, requestId, today = { today }, photos = SinglePhotoSaver(uploader, storage))

    private fun HelpRequestEditViewModel.fill() = update {
        it.copy(title = "Récupérer un colis", date = today.plusDays(2), notifyIds = setOf("adult2"), photo = PhotoEdit.Picked("content://qr"))
    }

    @Test
    fun `a new request sends its photo to my folder, then names it`() {
        val repo = FakeHelpRequests()
        val viewModel = edit(repo)
        viewModel.fill()
        viewModel.save()
        assertEquals(HelpRequestEditUiState.Done, viewModel.uiState.value)
        val photo = repo.saved.single().first.photo!!
        assertTrue(photo.matches(Regex("^helpRequests/r-new/adult1/[A-Za-z0-9]{20}$")))
        assertEquals(listOf("$photo.jpg"), sent)
    }

    @Test
    fun `a photo that cannot be sent keeps the request unsent`() {
        failUpload = true
        val repo = FakeHelpRequests()
        val viewModel = edit(repo)
        viewModel.fill()
        viewModel.save()
        assertTrue((viewModel.uiState.value as HelpRequestEditUiState.Editing).saveFailed)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `removing the photo saves without it, then deletes its files`() {
        val old = "helpRequests/r1/adult1/p1"
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2")).copy(photo = old)))
        val viewModel = edit(repo, requestId = "r1")
        assertEquals(old, (viewModel.uiState.value as HelpRequestEditUiState.Editing).storedPhoto)
        viewModel.update { it.copy(photo = PhotoEdit.Removed) }
        viewModel.save()
        assertNull(repo.saved.single().first.photo)
        assertEquals(listOf("$old.jpg", "$old-thumb.jpg"), storage.deleted)
    }

    @Test
    fun `an edit that keeps the photo keeps its name`() {
        val old = "helpRequests/r1/adult1/p1"
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2")).copy(photo = old)))
        val viewModel = edit(repo, requestId = "r1")
        viewModel.update { it.copy(title = "Récupérer deux colis") }
        viewModel.save()
        assertEquals(old, repo.saved.single().first.photo)
        assertTrue(sent.isEmpty() && storage.deleted.isEmpty())
    }
}
