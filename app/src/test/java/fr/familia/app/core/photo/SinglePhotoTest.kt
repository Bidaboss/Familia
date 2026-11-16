package fr.familia.app.core.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePhotoTest {

    private class FakeStorage(private val failing: Boolean = false) : PhotoStorage {
        val deleted = mutableListOf<String>()
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String) = "https://example.com/$path"
        override suspend fun delete(path: String) {
            if (failing) throw IllegalStateException("offline")
            deleted += path
        }
    }

    private val sent = mutableListOf<Triple<String, String, String>>()
    private val uploader = PhotoUploader { source, photo, thumb -> sent += Triple(source, photo, thumb) }

    @Test
    fun `the cover of an event lives in the folder of its sender, under the event`() {
        assertEquals("events/e1/cover/m1/p1", SinglePhoto.eventCover("e1", "m1", "p1"))
        assertEquals("events/e1/cover/m1/p1.jpg", SinglePhoto.photoPath("events/e1/cover/m1/p1"))
        assertEquals("events/e1/cover/m1/p1-thumb.jpg", SinglePhoto.thumbPath("events/e1/cover/m1/p1"))
        assertTrue(SinglePhoto.newId().matches(Regex("^[A-Za-z0-9]{20}$")))
    }

    @Test
    fun `a chosen photo is sent with its thumbnail before the document, under a new name`() = runTest {
        val saver = SinglePhotoSaver(uploader, FakeStorage())
        val base = saver.prepare("old", PhotoEdit.Picked("content://photo/1")) { "new" }
        assertEquals("new", base)
        assertEquals(listOf(Triple("content://photo/1", "new.jpg", "new-thumb.jpg")), sent)
    }

    @Test
    fun `nothing is sent when the photo is kept or removed`() = runTest {
        val saver = SinglePhotoSaver(uploader, FakeStorage())
        assertEquals("old", saver.prepare("old", PhotoEdit.Unchanged) { error("no new name") })
        assertNull(saver.prepare("old", PhotoEdit.Removed) { error("no new name") })
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `the photo the document no longer names is removed, never the one it keeps`() = runTest {
        val storage = FakeStorage()
        val saver = SinglePhotoSaver(uploader, storage)
        saver.cleanUp(previous = "old", saved = "old")
        saver.cleanUp(previous = null, saved = "new")
        assertTrue(storage.deleted.isEmpty())
        saver.cleanUp(previous = "old", saved = "new")
        assertEquals(listOf("old.jpg", "old-thumb.jpg"), storage.deleted)
    }

    @Test
    fun `a removal that fails never stops the save`() = runTest {
        SinglePhotoSaver(uploader, FakeStorage(failing = true)).cleanUp(previous = "old", saved = null)
    }
}
