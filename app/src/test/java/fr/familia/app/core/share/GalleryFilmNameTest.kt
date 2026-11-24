package fr.familia.app.core.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** The file names of the films saved in the gallery (récit 108, règle 32). */
class GalleryFilmNameTest {
    @Test
    fun `letters and digits only, accents taken off, never two dashes`() {
        assertEquals("familia-etape-ile-rousse-corse.mp4", galleryFilmName("etape", "Île-Rousse, Corse"))
        assertEquals("familia-voyage-ete-2027.mp4", galleryFilmName("voyage", "  Été  2027 !"))
        assertEquals("familia-film-s1-20270712.mp4", galleryFilmName("film", "s1_20270712"))
    }
}
