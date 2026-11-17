package fr.familia.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkTest {

    @Test
    fun `a link names only the kind and the id`() {
        assertEquals("https://familia-93286.web.app/o/event/abc123", shareLink(ShareKind.Event, "abc123"))
        assertEquals("https://familia-93286.web.app/o/birthday/m_1", shareLink(ShareKind.Birthday, "m_1"))
    }

    @Test
    fun `shared wishes carry their name and the link, never an item nor a reservation`() {
        val content = wishesShareContent("wishes-adult1", "Envies de Camille", "Les envies de Camille sont dans Familia.")
        assertTrue(content.lines.isEmpty())
        assertNull(content.photo)
        assertEquals("https://familia-93286.web.app/o/list/wishes-adult1", content.link)
        // The message is the text and the link, nothing else: no title of a wish, no giver.
        assertEquals("Les envies de Camille sont dans Familia.\nhttps://familia-93286.web.app/o/list/wishes-adult1", shareMessage(content))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an id that is not a document id is refused`() {
        shareLink(ShareKind.Poll, "../config")
    }

    @Test
    fun `every kind comes back from its own link`() {
        ShareKind.entries.forEach { kind ->
            assertEquals(SharedLink(kind, "x1"), parseSharedLink("https", SHARE_HOST, "/o/${kind.path}/x1"))
        }
    }

    @Test
    fun `the familia scheme of the page opens the same item`() {
        assertEquals(SharedLink(ShareKind.Poll, "p9"), parseSharedLink("familia", "o", "/poll/p9"))
    }

    @Test
    fun `other links are ignored`() {
        assertNull(parseSharedLink("https", "example.com", "/o/event/abc"))
        assertNull(parseSharedLink("http", SHARE_HOST, "/o/event/abc"))
        assertNull(parseSharedLink("https", SHARE_HOST, "/o/secret/abc"))
        assertNull(parseSharedLink("https", SHARE_HOST, "/o/event"))
        assertNull(parseSharedLink("https", SHARE_HOST, "/o/event/abc/more"))
        assertNull(parseSharedLink("https", SHARE_HOST, "/o/event/a%2Fb"))
        assertNull(parseSharedLink("familia", "x", "/event/abc"))
        assertNull(parseSharedLink(null, null, null))
    }

    @Test
    fun `a photo with children goes out only through one of their responsible adults (EXPERT-2)`() {
        assertTrue(photoAllowed("ines", emptyList()))
        assertTrue(photoAllowed("ines", listOf(listOf("ines", "sam"))))
        assertFalse(photoAllowed("camille", listOf(listOf("ines", "sam"))))
        // Two children: the sharer must be responsible for both.
        assertFalse(photoAllowed("ines", listOf(listOf("ines"), listOf("sam"))))
    }

    @Test
    fun `the manifest opens the links of the same host and scheme`() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:host="$SHARE_HOST""""))
        assertTrue(manifest.contains("""android:scheme="$SHARE_SCHEME" android:host="o""""))
    }

    @Test
    fun `the message ends with the link on its own line`() {
        val content = ShareContent("Repas", emptyList(), "Repas de Noël. Les détails sont dans Familia : ", "https://x/o/event/a")
        assertEquals("Repas de Noël. Les détails sont dans Familia :\nhttps://x/o/event/a", shareMessage(content))
    }
}
