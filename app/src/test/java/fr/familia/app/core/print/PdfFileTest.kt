package fr.familia.app.core.print

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** A PDF of Familia is written whole or not at all: never half a file left to share. */
class PdfFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val plenty: (File) -> Long = { 10L * 1024 * 1024 * 1024 }

    private fun left(folder: File) = folder.listFiles().orEmpty().map { it.name }.sorted()

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Throwable) {
        e
    }

    @Test
    fun `a finished PDF is renamed, and nothing else stays`() = runTest {
        val shares = temp.newFolder("shares")
        val book = File(shares, "livre-photo.pdf")
        val made = writePdfSafely(book, 1_000, plenty) { part -> part.writeText("%PDF") }
        assertEquals(book, made)
        assertEquals("%PDF", book.readText())
        assertEquals(listOf("livre-photo.pdf"), left(shares))
    }

    @Test
    fun `a writing cut in the middle leaves no file at all`() = runTest {
        val shares = temp.newFolder("shares")
        val book = File(shares, "livre-photo.pdf")
        val failed = failure { writePdfSafely(book, 1_000, plenty) { part -> part.writeText("%PDF half"); throw IOException("cut") } }
        assertTrue("$failed", failed is IOException && failed !is PrintException)
        assertEquals(emptyList<String>(), left(shares))
    }

    @Test
    fun `leaving the screen while it writes leaves no file either`() = runTest {
        val shares = temp.newFolder("shares")
        val failed = failure { writePdfSafely(File(shares, "a.pdf"), 1_000, plenty) { part -> part.writeText("%PDF"); throw CancellationException("gone") } }
        assertTrue("$failed", failed is CancellationException)
        assertEquals(emptyList<String>(), left(shares))
    }

    @Test
    fun `what an app stopped by Android left is removed at the next PDF`() = runTest {
        val shares = temp.newFolder("shares")
        File(shares, "arbre.pdf.part").writeText("%PDF half")
        writePdfSafely(File(shares, "livre.pdf"), 1_000, plenty) { part -> part.writeText("%PDF") }
        assertEquals(listOf("livre.pdf"), left(shares))
    }

    @Test
    fun `a phone without room says so, before writing anything`() = runTest {
        val shares = temp.newFolder("shares")
        var wrote = false
        val failed = failure { writePdfSafely(File(shares, "livre.pdf"), 80L * 1024 * 1024, { 50L * 1024 * 1024 }) { wrote = true } }
        assertEquals(PrintProblem.NoRoom, (failed as PrintException).problem)
        assertFalse(wrote)
    }

    @Test
    fun `a phone that fills up while writing says it has no room, not that the network failed`() = runTest {
        val shares = temp.newFolder("shares")
        var free = 1024L * 1024 * 1024
        val failed = failure {
            writePdfSafely(File(shares, "livre.pdf"), 1_000, { free }) { part ->
                part.writeText("%PDF")
                free = 0
                throw IOException("ENOSPC (No space left on device)")
            }
        }
        assertEquals(PrintProblem.NoRoom, (failed as PrintException).problem)
        assertEquals(emptyList<String>(), left(shares))
    }

    @Test
    fun `a phone short of memory for so many photos says the file is too big`() = runTest {
        val shares = temp.newFolder("shares")
        val failed = failure { writePdfSafely(File(shares, "livre.pdf"), 1_000, plenty) { throw OutOfMemoryError("200 photos") } }
        assertEquals(PrintProblem.TooBig, (failed as PrintException).problem)
        assertEquals(emptyList<String>(), left(shares))
    }

    @Test
    fun `a photo goes into the page at 1,5 pixel a point, never enlarged`() {
        // A quarter of an A4 landscape page, about 377 × 255 points, for a photo of 1600 × 1200.
        assertEquals(565 to 382, printSize(1600, 1082, 377f, 255f))
        // A small photo keeps its own pixels.
        assertEquals(400 to 300, printSize(400, 300, 377f, 255f))
    }
}
