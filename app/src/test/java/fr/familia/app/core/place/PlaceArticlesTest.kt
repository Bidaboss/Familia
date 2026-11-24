package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceArticlesTest {
    private val standard = """
        {"type":"standard","title":"La Samoure","extract":"La Samoure est une station balnéaire de Charente-Maritime.",
         "content_urls":{"desktop":{"page":"https://fr.wikivoyage.org/wiki/La_Samoure"}}}
    """.trimIndent()

    @Test
    fun `a standard answer gives the title, the text and the page`() {
        val article = parseArticle(standard)!!
        assertEquals("La Samoure", article.title)
        assertEquals("La Samoure est une station balnéaire de Charente-Maritime.", article.extract)
        assertEquals("https://fr.wikivoyage.org/wiki/La_Samoure", article.url)
    }

    @Test
    fun `a page that names several places gives nothing`() {
        assertNull(parseArticle("""{"type":"disambiguation","title":"Lyon","extract":"Lyon peut désigner","content_urls":{"desktop":{"page":"https://fr.wikivoyage.org/wiki/Lyon"}}}"""))
    }

    @Test
    fun `an empty text, a missing page or something unreadable give nothing`() {
        assertNull(parseArticle("""{"type":"standard","title":"X","extract":"  ","content_urls":{"desktop":{"page":"https://fr.wikivoyage.org/wiki/X"}}}"""))
        assertNull(parseArticle("""{"type":"standard","title":"X","extract":"Un texte"}"""))
        assertNull(parseArticle("pas du json"))
    }

    @Test
    fun `a page that is not secure is refused`() {
        assertNull(parseArticle("""{"type":"standard","title":"X","extract":"Un texte","content_urls":{"desktop":{"page":"http://exemple.fr"}}}"""))
    }

    @Test
    fun `a long text is cut at a word with an ellipsis`() {
        val long = "mot ".repeat(200).trim()
        val short = shorten(long)
        assertTrue(short.length <= ARTICLE_MAX_CHARS + 1)
        assertTrue(short.endsWith("mot…"))
        assertEquals("court", shorten("court"))
    }

    @Test
    fun `the address of a page encodes the place`() {
        assertEquals("https://fr.wikivoyage.org/api/rest_v1/page/summary/La_Samoure", summaryUrl("La Samoure"))
        assertEquals("https://fr.wikivoyage.org/api/rest_v1/page/summary/%C3%8Ele_de_R%C3%A9", summaryUrl(" Île de Ré "))
    }
}
