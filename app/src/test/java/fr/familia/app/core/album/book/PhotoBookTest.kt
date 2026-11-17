package fr.familia.app.core.album.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.print.PrintException
import fr.familia.app.core.print.PrintProblem
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Le livre photo d'un album (récit 59), with the fictional test family. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoBookTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun photo(author: Member, slot: Int, minute: Long? = slot.toLong()) =
        AlbumPhoto("trips/t1", author.id, author.firstName, slot, minute?.let { Instant.parse("2026-08-04T10:00:00Z").plusSeconds(it * 60) })

    private fun book(photo: AlbumPhoto, big: Boolean = false) = BookPhoto(photo, photo.id, big)

    private class FakeAlbum(photos: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(photos)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = photos
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    @Test
    fun `pages hold four photos at most and a big photo is alone on its page`() {
        val p = (1..7).map { book(photo(TestFamily.adult, it), big = it == 3) }
        assertEquals(listOf(listOf(p[0], p[1]), listOf(p[2]), listOf(p[3], p[4], p[5], p[6])), bookPages(p))
        assertEquals(emptyList<List<BookPhoto>>(), bookPages(emptyList()))
    }

    @Test
    fun `only a trip or an event can make a book`() {
        assertEquals(true, PhotoBookDestination(PhotoBookDestination.TRIP, "t1").isKnown)
        assertEquals(true, PhotoBookDestination(PhotoBookDestination.EVENT, "e1").isKnown)
        assertEquals(false, PhotoBookDestination("recipe", "r1").isKnown)
    }

    @Test
    fun `dates captions and file names read well`() {
        assertEquals("4 août 2026", bookDates(LocalDate.of(2026, 8, 4)))
        assertEquals("du 3 au 9 août 2026", bookDates(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9)))
        assertEquals("du 30 juillet au 2 août 2026", bookDates(LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 2)))
        assertEquals("du 30 décembre 2026 au 2 janvier 2027", bookDates(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)))
        assertEquals("Adult1, 4 août 2026", bookCaption(photo(TestFamily.adult, 1), ZoneOffset.UTC))
        assertEquals("Adult1", bookCaption(photo(TestFamily.adult, 1, minute = null)))
        assertEquals("livre-photo-vacances-a-la-mer-ete.pdf", photoBookFileName("Vacances à la mer, été !"))
        assertEquals("livre-photo.pdf", photoBookFileName("  ?? "))
    }

    @Test
    fun `the book takes every photo of the album, children's ones too, oldest first`() = runTest {
        val album = FakeAlbum(listOf(photo(TestFamily.adult, 2), photo(TestFamily.otherKid, 3), photo(TestFamily.adult, 1)))
        val vm = PhotoBookViewModel(album, source = { BookSource("trips/t1", "Vacances", "du 3 au 9 août 2026") }, print = { null })
        val state = vm.uiState.value as PhotoBookUiState.Choosing
        assertEquals(listOf("adult1_1", "adult1_2", "kid2_3"), state.photos.map { it.photo.id })
        assertEquals("adult1_1", state.coverId)
        assertEquals(2, state.pageCount)
    }

    @Test
    fun `I choose, move and enlarge the photos, and the cover follows`() = runTest {
        val album = FakeAlbum((1..3).map { photo(TestFamily.adult, it) })
        var printed: PhotoBookContent? = null
        val vm = PhotoBookViewModel(
            album,
            source = { BookSource("trips/t1", "Vacances", "4 août 2026", listOf(BookStep("Train", "Gare", LocalDate.of(2026, 8, 4)))) },
            print = { printed = it; File("livre.pdf") },
        )
        vm.toggle("adult1_1")
        vm.move("adult1_3", -1)
        vm.toggleBig("adult1_2")
        val state = vm.uiState.value as PhotoBookUiState.Choosing
        assertEquals("adult1_2", state.coverId)
        assertEquals(listOf("adult1_1", "adult1_3", "adult1_2"), state.photos.map { it.photo.id })
        assertEquals(4, state.pageCount)

        vm.make()

        val content = printed!!
        assertEquals("adult1_2", content.cover?.id)
        assertEquals(listOf(listOf("adult1_3"), listOf("adult1_2")), content.pages.map { page -> page.map { it.photo.id } })
        assertEquals(1, content.steps.size)
        assertEquals(File("livre.pdf"), vm.made.value)
        vm.shared()
        assertNull(vm.made.value)
    }

    @Test
    fun `an empty album or one that cannot be read gives the empty screen`() = runTest {
        val vm = PhotoBookViewModel(FakeAlbum(emptyList()), source = { BookSource("trips/t1", "V", "") }, print = { null })
        assertEquals(PhotoBookUiState.Empty, vm.uiState.value)
        val gone = PhotoBookViewModel(FakeAlbum(listOf(photo(TestFamily.adult, 1))), source = { null }, print = { null })
        assertEquals(PhotoBookUiState.Empty, gone.uiState.value)
    }

    @Test
    fun `a phone without room or memory is said so, not the connection`() = runTest {
        val album = FakeAlbum((1..2).map { photo(TestFamily.adult, it) })
        var problem: PrintProblem = PrintProblem.NoRoom
        val vm = PhotoBookViewModel(album, source = { BookSource("trips/t1", "Vacances", "") }, print = { throw PrintException(problem) })

        vm.make()
        var state = vm.uiState.value as PhotoBookUiState.Choosing
        assertEquals(true, state.failed)
        assertEquals(PrintProblem.NoRoom, state.problem)
        assertNull(vm.made.value)

        problem = PrintProblem.TooBig
        vm.make()
        state = vm.uiState.value as PhotoBookUiState.Choosing
        assertEquals(PrintProblem.TooBig, state.problem)

        // Another failure (the network): the usual message, with no problem of the phone.
        val offline = PhotoBookViewModel(album, source = { BookSource("trips/t1", "Vacances", "") }, print = { throw IOException("offline") })
        offline.make()
        val off = offline.uiState.value as PhotoBookUiState.Choosing
        assertEquals(true, off.failed)
        assertNull(off.problem)

        // Changing the book clears the message.
        vm.toggle("adult1_1")
        assertNull((vm.uiState.value as PhotoBookUiState.Choosing).problem)
    }
}
