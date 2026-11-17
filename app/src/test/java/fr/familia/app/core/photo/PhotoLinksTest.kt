package fr.familia.app.core.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The links of the photos, asked once per launch and kept for a weak network (démo du 21). */
class PhotoLinksTest {

    private class Memory : SavedLinks {
        val links = HashMap<String, String>()
        var writes = 0
        override fun get(path: String): String? = links[path]
        override fun put(path: String, url: String?) {
            writes++
            if (url == null) links.remove(path) else links[path] = url
        }
    }

    @Test
    fun `a link is asked once per launch, and kept for the next one`() = runTest {
        val saved = Memory()
        var asked = 0
        val first = PhotoLinks(saved)
        assertNull(first.known("trips/t1/a/1.jpg"))
        assertEquals("https://x/1?token=a", first.fresh("trips/t1/a/1.jpg") { asked++; "https://x/1?token=a" })
        assertEquals("https://x/1?token=a", first.fresh("trips/t1/a/1.jpg") { asked++; "never" })
        assertEquals(1, asked)

        // The next launch knows it before the network answers, and asks once again.
        val next = PhotoLinks(saved)
        assertEquals("https://x/1?token=a", next.known("trips/t1/a/1.jpg"))
        assertEquals("https://x/1?token=b", next.fresh("trips/t1/a/1.jpg") { asked++; "https://x/1?token=b" })
        assertEquals(2, asked)
        assertEquals("https://x/1?token=b", saved.get("trips/t1/a/1.jpg"))
    }

    @Test
    fun `the same link is not written again, and a removed photo is forgotten`() = runTest {
        val saved = Memory()
        PhotoLinks(saved).fresh("p") { "u" }
        PhotoLinks(saved).fresh("p") { "u" }
        assertEquals(1, saved.writes)
        val links = PhotoLinks(saved)
        links.forget("p")
        assertNull(links.known("p"))
        assertNull(PhotoLinks(saved).known("p"))
    }
}
