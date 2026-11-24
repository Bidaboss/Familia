package fr.familia.app.core.album

import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.PhotoFilmMaker
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumFilmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeStorage : PhotoStorage {
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String): String = "https://example.com/$path"
        override suspend fun delete(path: String) = Unit
    }

    private class FakeMaker(private val fail: Boolean = false) : PhotoFilmMaker {
        var urls: List<String> = emptyList()
        var seconds = 0.0
        var style: FilmStyle? = null
        override suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File, style: FilmStyle) {
            if (fail) throw IOException("no encoder")
            urls = photoUrls
            seconds = secondsPerPhoto
            this.style = style
        }
    }

    private fun photo(author: String, slot: Int, second: Long) = AlbumPhoto("trips/t1", author, author, slot, Instant.ofEpochSecond(second))

    @Test
    fun `the film shows every photo, oldest first, made on the phone`() = runTest {
        val maker = FakeMaker()
        val vm = AlbumFilmViewModel(FakeStorage(), maker, "voyage-t1", File("films"))
        vm.make(listOf(photo("adult2", 1, 30), photo("kid1", 4, 10), photo("adult1", 2, 20)))

        assertEquals(
            listOf("trips/t1/kid1/4.jpg", "trips/t1/adult1/2.jpg", "trips/t1/adult2/1.jpg").map { "https://example.com/$it" },
            maker.urls,
        )
        assertEquals(3.0, maker.seconds, 0.0)
        val ready = vm.uiState.value as FilmState.Ready
        assertTrue(ready.path.endsWith("voyage-t1.mp4"))
    }

    @Test
    fun `about a minute whatever the number of photos, never under 1,5 s a photo`() {
        assertEquals(3.0, AlbumFilmViewModel.secondsPerPhoto(5), 0.0)
        assertEquals(2.0, AlbumFilmViewModel.secondsPerPhoto(30), 0.0)
        assertEquals(1.5, AlbumFilmViewModel.secondsPerPhoto(200), 0.0)
    }

    @Test
    fun `one photo is not enough, and a failure is said`() = runTest {
        val vm = AlbumFilmViewModel(FakeStorage(), FakeMaker(fail = true), "voyage-t1", File("films"))
        vm.make(listOf(photo("adult2", 1, 30)))
        assertEquals(FilmState.Idle, vm.uiState.value)
        vm.make(listOf(photo("adult2", 1, 30), photo("adult2", 2, 40)))
        assertEquals(FilmState.Failed, vm.uiState.value)
    }

    @Test
    fun `a film of a step keeps 2,5 s a photo and the look of the trips`() = runTest {
        val maker = FakeMaker()
        val vm = AlbumFilmViewModel(FakeStorage(), maker, "etape-t1-s1", File("films"), FilmStyle.Trip) { 2.5 }
        vm.make(listOf(photo("adult2", 1, 30), photo("kid1", 4, 10)))
        assertEquals(2.5, maker.seconds, 0.0)
        assertEquals(FilmStyle.Trip, maker.style)
        val ready = vm.uiState.value as FilmState.Ready
        assertTrue(ready.path.endsWith("etape-t1-s1.mp4"))
    }
}
