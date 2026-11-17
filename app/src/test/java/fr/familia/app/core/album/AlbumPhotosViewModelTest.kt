package fr.familia.app.core.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoRefusedException
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.UnreadablePhotoException
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.photos.tripAlbumRights
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** The album of a trip of TestTrips: adult1 created it and goes with adult2, everyone follows. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumPhotosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAlbum(initial: List<AlbumPhoto> = emptyList()) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        val added = mutableListOf<Pair<Int, String>>()
        val deleted = mutableListOf<String>()
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> =
            photos.map { list -> list.filter { it.folder == folder }.sortedWith(albumPhotoOrder) }
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) {
            added += slot to me.id
            photos.value = photos.value + AlbumPhoto(folder, me.id, me.firstName, slot, createdAt = null)
        }
        val takenAts = mutableListOf<Instant?>()
        override suspend fun addPhoto(folder: String, slot: Int, me: Member, takenAt: Instant?) {
            takenAts += takenAt
            addPhoto(folder, slot, me)
        }
        override suspend fun deletePhoto(photo: AlbumPhoto) {
            deleted += photo.id
            photos.value = photos.value.filterNot { it.id == photo.id && it.folder == photo.folder }
        }
    }

    /** Remembers what was sent, and refuses the sources listed in [failing]. */
    private class FakeUploader(
        private val failing: Set<String> = emptySet(),
        private val refused: Set<String> = emptySet(),
        private val dates: Map<String, Instant> = emptyMap(),
    ) : PhotoUploader {
        val sent = mutableListOf<Triple<String, String, String>>()
        override suspend fun takenAt(source: String): Instant? = dates[source]
        override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) {
            if (source in failing) throw UnreadablePhotoException("unreadable")
            if (source in refused) throw PhotoRefusedException(IllegalStateException("rules"))
            sent += Triple(source, photoPath, thumbnailPath)
        }
    }

    private class FakeStorage : PhotoStorage {
        val deleted = mutableListOf<String>()
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String): String = "https://example.com/$path"
        override suspend fun delete(path: String) {
            deleted += path
        }
    }

    private fun photo(author: String, slot: Int) =
        AlbumPhoto("trips/t1", author, author.replaceFirstChar { it.uppercase() }, slot, Instant.ofEpochSecond(slot.toLong()))

    private fun album(
        me: String,
        photos: FakeAlbum,
        uploader: PhotoUploader = FakeUploader(),
        storage: PhotoStorage = FakeStorage(),
        saved: SavedStateHandle = SavedStateHandle(),
    ): AlbumPhotosViewModel {
        val rights = tripAlbumRights(FakeTrips(), FakeMembers(TestFamily.family), "t1", me)
        return AlbumPhotosViewModel(photos, rights, uploader, storage, me, "trips/t1", savedState = saved)
    }

    @Test
    fun `photos chosen before the album is read go as soon as it is`() = runTest {
        val photos = FakeAlbum()
        val uploader = FakeUploader()
        val vm = album("adult2", photos, uploader)

        // The gallery answers the reopened app before the album is read.
        vm.addPhotos(listOf("content://a"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(listOf("content://a"), uploader.sent.map { it.first })
        assertEquals(listOf(1 to "adult2"), photos.added)
    }

    @Test
    fun `photos not sent yet go again after Android stops the app, and only once`() = runTest {
        val saved = SavedStateHandle()
        val stuck = object : PhotoUploader {
            override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) = awaitCancellation()
        }
        val first = album("adult2", FakeAlbum(), stuck, saved = saved)
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.uiState.collect {} }
        first.addPhotos(listOf("content://a", "content://b"))
        collecting.cancel()

        // Android stops the app: a new screen starts from what was kept.
        val photos = FakeAlbum()
        val uploader = FakeUploader()
        val again = album("adult2", photos, uploader, saved = saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { again.uiState.collect {} }

        assertEquals(listOf("content://a", "content://b"), uploader.sent.map { it.first })
        assertEquals(listOf(1 to "adult2", 2 to "adult2"), photos.added)
        // Once sent, they leave the list: a third start sends nothing.
        val none = FakeUploader()
        val third = album("adult2", FakeAlbum(photos.photos.value), none, saved = saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { third.uiState.collect {} }
        assertTrue(none.sent.isEmpty())
    }

    @Test
    fun `a participant sends photos in free places, files first, then the record`() = runTest {
        val photos = FakeAlbum(listOf(photo("adult2", 1), photo("adult2", 3)))
        val uploader = FakeUploader()
        val vm = album("adult2", photos, uploader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.canAdd)
        assertEquals(28, vm.uiState.value.placesLeft)

        vm.addPhotos(listOf("content://a", "content://b"))

        assertEquals(
            listOf(
                Triple("content://a", "trips/t1/adult2/2.jpg", "trips/t1/adult2/2-thumb.jpg"),
                Triple("content://b", "trips/t1/adult2/4.jpg", "trips/t1/adult2/4-thumb.jpg"),
            ),
            uploader.sent,
        )
        assertEquals(listOf(2 to "adult2", 4 to "adult2"), photos.added)
        assertEquals(0, vm.uiState.value.sending)
        assertEquals(26, vm.uiState.value.placesLeft)
        assertNull(vm.uiState.value.problem)
    }

    @Test
    fun `the day a photo was taken goes with its record when the file says it`() = runTest {
        val photos = FakeAlbum()
        val day = Instant.parse("2025-08-04T16:30:00Z")
        val vm = album("adult2", photos, FakeUploader(dates = mapOf("content://a" to day)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos(listOf("content://a", "content://b"))

        assertEquals(listOf(day, null), photos.takenAts)
    }

    @Test
    fun `no more than 30 photos per person, and the extra ones are said`() = runTest {
        val photos = FakeAlbum((1..29).map { photo("adult2", it) })
        val vm = album("adult2", photos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos(listOf("content://a", "content://b"))

        assertEquals(listOf(30 to "adult2"), photos.added)
        assertEquals(PhotoProblem.TooMany, vm.uiState.value.problem)
        assertEquals(0, vm.uiState.value.placesLeft)
    }

    @Test
    fun `a photo that cannot be sent is said, and the others still go`() = runTest {
        val photos = FakeAlbum()
        val vm = album("adult2", photos, FakeUploader(setOf("content://a")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos(listOf("content://a", "content://b"))

        assertEquals(listOf(2 to "adult2"), photos.added)
        assertEquals(PhotoProblem.Unreadable, vm.uiState.value.problem)
        vm.dismissProblem()
        assertNull(vm.uiState.value.problem)
    }

    @Test
    fun `a photo the server refuses is said as such, not as a network problem`() = runTest {
        val photos = FakeAlbum()
        val vm = album("adult2", photos, FakeUploader(refused = setOf("content://a")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos(listOf("content://a"))

        assertTrue(photos.added.isEmpty())
        assertEquals(PhotoProblem.Refused, vm.uiState.value.problem)
    }

    @Test
    fun `a follower sees the photos but adds none and removes none`() = runTest {
        val uploader = FakeUploader()
        val vm = album("kid1", FakeAlbum(listOf(photo("adult2", 1))), uploader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertFalse(vm.uiState.value.canAdd)
        assertEquals(1, vm.uiState.value.photos.size)
        vm.addPhotos(listOf("content://a"))
        assertTrue(uploader.sent.isEmpty())
        assertFalse(vm.uiState.value.canRemove(vm.uiState.value.photos[0]))
    }

    @Test
    fun `the creator of the trip removes a photo, its files first`() = runTest {
        val photos = FakeAlbum(listOf(photo("adult2", 1), photo("adult2", 2)))
        val storage = FakeStorage()
        val vm = album("adult1", photos, storage = storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.removePhoto(photo("adult2", 1))

        assertEquals(listOf("trips/t1/adult2/1.jpg", "trips/t1/adult2/1-thumb.jpg"), storage.deleted)
        assertEquals(listOf("adult2_1"), photos.deleted)
        assertEquals(listOf("adult2_2"), vm.uiState.value.photos.map { it.id })
    }

    @Test
    fun `newest photos come first`() = runTest {
        val vm = album("kid1", FakeAlbum(listOf(photo("adult2", 1), photo("adult1", 5), photo("adult2", 3))))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("adult1_5", "adult2_3", "adult2_1"), vm.uiState.value.photos.map { it.id })
    }

    /** Sends each photo only when [gate] lets one through: the test says when a photo is done. */
    private class GatedUploader(private val failing: Set<String> = emptySet()) : PhotoUploader {
        val gate = Channel<Unit>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) {
            gate.receive()
            if (source in failing) throw UnreadablePhotoException("a video")
            sent += source
        }
    }

    private fun TestScope.appSends() = AlbumSends(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)))

    private fun album(me: String, photos: FakeAlbum, uploader: PhotoUploader, sends: AlbumSends) =
        AlbumPhotosViewModel(
            photos,
            tripAlbumRights(FakeTrips(), FakeMembers(TestFamily.family), "t1", me),
            uploader,
            FakeStorage(),
            me,
            "trips/t1",
            sends = sends,
        )

    @Test
    fun `30 photos chosen at once go one after the other, and the bar counts them`() = runTest {
        val photos = FakeAlbum()
        val uploader = GatedUploader()
        val sends = appSends()
        val vm = album("adult2", photos, uploader, sends)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos((1..30).map { "content://p$it" })
        assertEquals(30, vm.uiState.value.sending)
        assertEquals(0f, vm.uiState.value.sendProgress)

        repeat(10) { uploader.gate.send(Unit) }
        assertEquals(10, vm.uiState.value.sent)
        assertEquals(20, vm.uiState.value.sending)
        assertEquals(1f / 3, vm.uiState.value.sendProgress, 0.001f)

        repeat(20) { uploader.gate.send(Unit) }
        assertEquals((1..30).toList(), photos.added.map { it.first })
        assertEquals(0, vm.uiState.value.sending)
        assertEquals(0, vm.uiState.value.sent)
        assertNull(vm.uiState.value.problem)
    }

    @Test
    fun `a video chosen by mistake among the photos is skipped, the others go`() = runTest {
        val photos = FakeAlbum()
        val uploader = GatedUploader(failing = setOf("content://video"))
        val vm = album("adult2", photos, uploader, appSends())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.addPhotos(listOf("content://a", "content://video", "content://b"))
        repeat(3) { uploader.gate.send(Unit) }

        assertEquals(listOf("content://a", "content://b"), uploader.sent)
        assertEquals(2, photos.added.size)
        assertEquals(PhotoProblem.Unreadable, vm.uiState.value.problem)
    }

    @Test
    fun `leaving the album does not stop the photos, and coming back shows them still going`() = runTest {
        val photos = FakeAlbum()
        val uploader = GatedUploader()
        val sends = appSends()
        val store = ViewModelStore()
        val first = album("adult2", photos, uploader, sends)
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = first as T
        })[AlbumPhotosViewModel::class.java]
        val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.uiState.collect {} }
        first.addPhotos(listOf("content://a", "content://b", "content://c"))
        uploader.gate.send(Unit)

        // The member goes back: the screen and its ViewModel are gone.
        collecting.cancel()
        store.clear()

        val again = album("adult2", photos, uploader, sends)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { again.uiState.collect {} }
        assertEquals(2, again.uiState.value.sending)
        assertEquals(1, again.uiState.value.sent)
        // A new choice never takes a place still being sent.
        again.addPhotos(listOf("content://d"))

        repeat(3) { uploader.gate.send(Unit) }
        assertEquals(listOf(1, 2, 3, 4), photos.added.map { it.first }.sorted())
        assertEquals(0, again.uiState.value.sending)
    }
}
