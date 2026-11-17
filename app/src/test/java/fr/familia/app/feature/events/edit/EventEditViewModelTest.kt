package fr.familia.app.feature.events.edit

import fr.familia.app.core.ui.Confirmation
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
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
import java.time.LocalTime

class EventEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(events: FakeEvents, me: String = "adult1", eventId: String? = null) =
        EventEditViewModel(events, FakeMembers(), me, eventId)

    private fun EventEditViewModel.editing() = uiState.value as EventEditUiState.Editing

    @Test
    fun `a new event is for the whole family by default, and lists what this member may choose`() {
        val state = vm(FakeEvents()).editing()
        assertTrue(state.isNew)
        assertEquals(EventTarget.ForFamily, state.form.target)
        assertEquals(4, state.targets.size)
        assertNull(state.targets.first { it.target is EventTarget.ForSelf }.name) // "Moi"
        assertEquals("Kid1", state.targets.first { it.target is EventTarget.ForChild }.name)
    }

    @Test
    fun `a child's new event is for themselves`() {
        assertEquals(EventTarget.ForSelf("kid1"), vm(FakeEvents(), me = "kid1").editing().form.target)
    }

    @Test
    fun `a title and a date are required, shown after the first try`() {
        val viewModel = vm(FakeEvents())
        assertEquals(emptySet<EventFormError>(), viewModel.editing().errors)
        viewModel.save()
        assertEquals(setOf(EventFormError.TitleMissing, EventFormError.DateMissing), viewModel.editing().errors)
    }

    @Test
    fun `saving a new event records the author and closes the form`() {
        val events = FakeEvents()
        val viewModel = vm(events)
        viewModel.update { it.copy(title = " Repas ", date = LocalDate.of(2026, 12, 24), time = LocalTime.of(19, 30), place = " ") }
        viewModel.save()
        assertEquals(EventEditUiState.Done(Confirmation.Saved), viewModel.uiState.value)
        val (event, isNew) = events.saved.single()
        assertTrue(isNew)
        assertEquals("e-new", event.id)
        assertEquals("Repas", event.title)
        assertNull(event.place)
        assertEquals("adult1", event.authorId)
        assertEquals("Adult1", event.authorName)
    }

    @Test
    fun `a parent edits their child's event and keeps its author`() {
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.kid, EventTarget.ForSelf("kid1"))))
        val viewModel = vm(events, eventId = "e1")
        assertFalse(viewModel.editing().isNew)
        // The child's own target stays available, with the child's name.
        assertEquals("Kid1", viewModel.editing().targets.first().name)
        viewModel.update { it.copy(title = "Piscine") }
        viewModel.save()
        val (event, isNew) = events.saved.single()
        assertFalse(isNew)
        assertEquals("kid1", event.authorId)
        assertEquals(EventTarget.ForSelf("kid1"), event.target)
    }

    @Test
    fun `someone else's event does not open`() {
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.otherAdult)))
        assertEquals(EventEditUiState.Unavailable, vm(events, eventId = "e1").uiState.value)
        assertEquals(EventEditUiState.Unavailable, vm(events, eventId = "gone").uiState.value)
    }

    @Test
    fun `deleting removes the event and closes the form`() {
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult)))
        val viewModel = vm(events, eventId = "e1")
        viewModel.delete()
        assertEquals(listOf("e1"), events.deleted)
        assertEquals(EventEditUiState.Done(Confirmation.Deleted), viewModel.uiState.value)
    }

    @Test
    fun `a refused write keeps the form open`() {
        val viewModel = vm(FakeEvents(failWith = IOException("denied")))
        viewModel.update { it.copy(title = "Repas", date = LocalDate.of(2026, 12, 24)) }
        viewModel.save()
        assertTrue(viewModel.editing().saveFailed)
    }

    @Test
    fun `offline, a pending write closes the form after the timeout`() = runTest {
        val viewModel = vm(FakeEvents(hang = true))
        viewModel.update { it.copy(title = "Repas", date = LocalDate.of(2026, 12, 24)) }
        viewModel.save()
        assertTrue(viewModel.editing().isSaving)
        advanceTimeBy(5_000)
        assertEquals(EventEditUiState.Done(Confirmation.Queued), viewModel.uiState.value)
    }

    @Test
    fun `the form knows when it changed`() {
        val viewModel = vm(FakeEvents())
        viewModel.update { it.copy(title = "R") }
        assertTrue(viewModel.editing().hasChanges)
        viewModel.update { it.copy(title = "") }
        assertFalse(viewModel.editing().hasChanges)
    }

    @Test
    fun `a draft comes back after Android closed the app in the background`() {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = EventEditViewModel(FakeEvents(), FakeMembers(), "adult1", null, savedState = handle)
        first.update { it.copy(title = "Galette", date = LocalDate.of(2027, 1, 6), time = LocalTime.of(16, 0), target = EventTarget.ForHousehold("h1")) }

        val again = EventEditViewModel(FakeEvents(), FakeMembers(), "adult1", null, savedState = handle)
        val form = again.editing().form
        assertEquals("Galette", form.title)
        assertEquals(LocalDate.of(2027, 1, 6), form.date)
        assertEquals(LocalTime.of(16, 0), form.time)
        assertEquals(EventTarget.ForHousehold("h1"), form.target)
        assertTrue(again.editing().hasChanges)
    }

    @Test
    fun `every target survives its encoding`() {
        listOf(EventTarget.ForFamily, EventTarget.ForHousehold("h1"), EventTarget.ForSelf("a"), EventTarget.ForChild("k"))
            .forEach { assertEquals(it, decodeTarget(encodeTarget(it))) }
        assertNull(decodeTarget("other:x"))
    }

    private class Photos(private val failUpload: Boolean = false) {
        val sent = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val saver = SinglePhotoSaver(
            uploader = { _, photo, _ ->
                if (failUpload) throw IOException("offline")
                sent += photo
            },
            storage = object : PhotoStorage {
                override suspend fun upload(path: String, jpeg: ByteArray) = Unit
                override suspend fun downloadUrl(path: String) = path
                override suspend fun delete(path: String) { deleted += path }
            },
        )
    }

    @Test
    fun `without « Plus de photos » the form shows no cover`() {
        assertFalse(vm(FakeEvents()).editing().showPhoto)
    }

    @Test
    fun `a chosen cover is sent before the event, in my folder, and named by it`() {
        val events = FakeEvents()
        val photos = Photos()
        val viewModel = EventEditViewModel(events, FakeMembers(), "adult1", null, photos = photos.saver)
        assertTrue(viewModel.editing().showPhoto)
        viewModel.update { it.copy(title = "Repas", date = LocalDate.of(2026, 12, 24), photo = PhotoEdit.Picked("content://photo/1")) }
        viewModel.save()
        val cover = events.saved.single().first.coverPhoto!!
        assertTrue(cover.startsWith("events/e-new/cover/adult1/"))
        assertEquals(listOf("$cover.jpg"), photos.sent)
        assertTrue(photos.deleted.isEmpty())
    }

    @Test
    fun `changing the cover of an event keeps nothing of the old one`() {
        val old = "events/e1/cover/adult2/old"
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult).copy(coverPhoto = old)))
        val photos = Photos()
        val viewModel = EventEditViewModel(events, FakeMembers(), "adult1", "e1", photos = photos.saver)
        assertEquals(old, viewModel.editing().storedCover)
        viewModel.update { it.copy(photo = PhotoEdit.Removed) }
        viewModel.save()
        assertNull(events.saved.single().first.coverPhoto)
        assertEquals(listOf("$old.jpg", "$old-thumb.jpg"), photos.deleted)
    }

    @Test
    fun `editing the rest keeps the cover`() {
        val old = "events/e1/cover/adult1/old"
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult).copy(coverPhoto = old)))
        val viewModel = EventEditViewModel(events, FakeMembers(), "adult1", "e1", photos = Photos().saver)
        viewModel.update { it.copy(title = "Goûter") }
        viewModel.save()
        assertEquals(old, events.saved.single().first.coverPhoto)
    }

    @Test
    fun `a cover that cannot be sent keeps the form open and writes nothing`() {
        val events = FakeEvents()
        val viewModel = EventEditViewModel(events, FakeMembers(), "adult1", null, photos = Photos(failUpload = true).saver)
        viewModel.update { it.copy(title = "Repas", date = LocalDate.of(2026, 12, 24), photo = PhotoEdit.Picked("content://photo/1")) }
        viewModel.save()
        assertTrue(viewModel.editing().saveFailed)
        assertTrue(events.saved.isEmpty())
    }
}
