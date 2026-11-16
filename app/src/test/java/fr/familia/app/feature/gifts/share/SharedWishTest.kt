package fr.familia.app.feature.gifts.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What shops and browsers share, with fictional links. */
class SharedWishTest {

    @Test
    fun `the subject the app gives is the title`() {
        assertEquals(
            SharedWish("https://boutique.example/echarpe-rouge", "Écharpe rouge en laine"),
            parseSharedWish("https://boutique.example/echarpe-rouge", "Écharpe rouge en laine"),
        )
    }

    @Test
    fun `without a subject, the text around the link is the title`() {
        assertEquals(
            SharedWish("https://boutique.example/p/123?ref=app", "Regarde ce produit"),
            parseSharedWish("Regarde ce produit : https://boutique.example/p/123?ref=app.", null),
        )
    }

    @Test
    fun `a link alone gives an empty title, to type`() {
        assertEquals(SharedWish("http://livres.example/a", ""), parseSharedWish("  http://livres.example/a ", "  "))
    }

    @Test
    fun `no web link, nothing to add`() {
        assertNull(parseSharedWish("Une écharpe rouge", null))
        assertNull(parseSharedWish(null, "Sujet"))
        assertNull(parseSharedWish("javascript:alert(1)", null))
    }

    @Test
    fun `tracking parameters past the limit of an item are dropped`() {
        val page = "https://boutique.example/p/echarpe-rouge"
        val shared = parseSharedWish("$page?utm_source=app&ref=" + "x".repeat(600), "Écharpe")
        assertEquals(page, shared!!.url)
        assertNull(parseSharedWish("https://boutique.example/" + "p".repeat(600), "Écharpe"))
    }

    @Test
    fun `a long title is cut to the limit of an item`() {
        assertEquals(80, parseSharedWish("https://a.example", "x".repeat(200))!!.title.length)
    }
}
