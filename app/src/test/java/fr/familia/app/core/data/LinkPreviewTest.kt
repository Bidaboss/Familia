package fr.familia.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Open Graph tags of fictional shop pages. */
class LinkPreviewTest {

    @Test
    fun `the open graph tags give the title, picture and price`() {
        val html = """
            <html><head>
            <title>Boutique — Écharpe</title>
            <meta property="og:title" content="Écharpe rouge &amp; douce">
            <meta content='https://cdn.boutique.example/echarpe.jpg' property='og:image' />
            <meta property="product:price:amount" content="24,90">
            </head></html>
        """.trimIndent()
        assertEquals(
            LinkPreview("Écharpe rouge & douce", "https://cdn.boutique.example/echarpe.jpg", 24.9),
            parseLinkPreview(html, base = "https://boutique.example/p/1"),
        )
    }

    @Test
    fun `without open graph, the page title, and a relative picture is made whole`() {
        val html = """<head><title> Un livre </title><meta name="twitter:image" content="/img/livre.png"></head>"""
        assertEquals(
            LinkPreview("Un livre", "https://livres.example/img/livre.png", null),
            parseLinkPreview(html, base = "https://livres.example/a/b"),
        )
    }

    @Test
    fun `a picture that is not https is left out, as the rules ask`() {
        val html = """<meta property="og:title" content="Jeu"><meta property="og:image" content="http://jeux.example/j.png">"""
        assertNull(parseLinkPreview(html, base = "http://jeux.example/")!!.imageUrl)
    }

    @Test
    fun `a price in another currency is left out`() {
        val html = """<meta property="og:title" content="Montre"><meta property="product:price:amount" content="99">""" +
            """<meta property="product:price:currency" content="USD">"""
        assertNull(parseLinkPreview(html, base = "https://a.example")!!.price)
    }

    @Test
    fun `a page in latin-1 is read in its own charset`() {
        val bytes = """<meta charset="iso-8859-1"><title>Écharpe</title>""".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Écharpe", parseLinkPreview(decodePage(bytes, "text/html"), base = "https://a.example")!!.title)
        assertEquals("Écharpe", decodePage("Écharpe".toByteArray(), "text/html; charset=UTF-8"))
    }

    @Test
    fun `a page with nothing to say gives no preview`() {
        assertNull(parseLinkPreview("<html><body>Bonjour</body></html>", base = "https://a.example"))
    }
}
