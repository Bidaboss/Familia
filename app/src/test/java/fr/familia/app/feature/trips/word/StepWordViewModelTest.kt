package fr.familia.app.feature.trips.word

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.TripWordRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StepWordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWords(initial: Map<String, TripWord> = emptyMap()) : TripWordRepository {
        val words = MutableStateFlow(initial)
        val saved = mutableListOf<List<Any>>()
        val removed = mutableListOf<String>()
        val hearts = mutableListOf<Pair<String, Boolean>>()
        override fun observeWords(tripId: String): Flow<Map<String, TripWord>> = words
        override fun observeWord(tripId: String, stepId: String): Flow<TripWord?> = words.map { it[stepId] }
        override suspend fun saveWord(tripId: String, stepId: String, text: String, photoIds: List<String>, me: Member, isNew: Boolean) {
            saved += listOf(stepId, text, photoIds, isNew)
        }
        override suspend fun deleteWord(tripId: String, stepId: String) { removed += stepId }
        override suspend fun setHeart(tripId: String, stepId: String, memberId: String, hearted: Boolean) { hearts += memberId to hearted }
        override fun comments(tripId: String, stepId: String): EventCommentRepository = error("not under test")
    }

    private class FakeAlbum(initial: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = photos
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    private val album = listOf(1, 2, 3, 4, 5).map { AlbumPhoto("trips/t1", "adult1", "Adult1", it, Instant.EPOCH) }

    private fun kotlinx.coroutines.test.TestScope.model(memberId: String, words: FakeWords, trips: FakeTrips = FakeTrips(steps = mapOf("t1" to listOf(TestTrips.step()))), photos: List<AlbumPhoto> = album): StepWordViewModel {
        val vm = StepWordViewModel(trips, words, FakeAlbum(photos), FakeMembers(), memberId, "t1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun StepWordViewModel.ready() = uiState.value as StepWordUiState.Ready

    private fun word(author: String = "adult1", hearts: Set<String> = emptySet()) =
        TripWord("s1", "Bien arrivés", listOf("adult1_2"), author, author, hearts)

    @Test
    fun `a participant writes a first word and publishes it, the photos in the order chosen`() = runTest {
        val words = FakeWords()
        val vm = model("adult1", words)
        assertTrue(vm.ready().canWrite)
        assertFalse(vm.ready().canPublish)
        vm.setText("  Il fait beau  ")
        vm.togglePhoto("adult1_3")
        vm.togglePhoto("adult1_1")
        assertTrue(vm.ready().canPublish)
        vm.publish()
        assertEquals(listOf(listOf("s1", "Il fait beau", listOf("adult1_3", "adult1_1"), true)), words.saved)
        assertTrue(vm.done.value)
    }

    @Test
    fun `a blank word is not published, and a fifth photo is not chosen`() = runTest {
        val words = FakeWords()
        val vm = model("adult1", words)
        vm.setText("   ")
        vm.togglePhoto("adult1_1")
        vm.publish()
        assertTrue(words.saved.isEmpty())
        listOf("adult1_2", "adult1_3", "adult1_4", "adult1_5").forEach(vm::togglePhoto)
        assertEquals(4, vm.ready().draft.photoIds.size)
    }

    @Test
    fun `the word is cut at 280 characters while typing`() = runTest {
        val vm = model("adult1", FakeWords())
        vm.setText("a".repeat(400))
        assertEquals(TripWord.MAX_TEXT, vm.ready().draft.text.length)
    }

    @Test
    fun `a photo gone from the album is dropped when the word is saved`() = runTest {
        val words = FakeWords(mapOf("s1" to word()))
        val vm = model("adult1", words, photos = album.filter { it.slot != 2 })
        vm.setText("Changé")
        vm.publish()
        assertEquals(listOf(listOf("s1", "Changé", emptyList<String>(), false)), words.saved)
    }

    @Test
    fun `only the author of a word changes or removes it`() = runTest {
        val words = FakeWords(mapOf("s1" to word(author = "adult1")))
        val other = model("adult2", words)
        assertFalse(other.ready().canWrite)
        assertTrue(other.ready().isParticipant)
        other.remove()
        assertTrue(words.removed.isEmpty())
        val author = model("adult1", words)
        author.remove()
        assertEquals(listOf("s1"), words.removed)
    }

    @Test
    fun `a follower reads, puts a heart and takes it back`() = runTest {
        val words = FakeWords(mapOf("s1" to word()))
        val vm = model("kid1", words)
        assertFalse(vm.ready().canWrite)
        assertFalse(vm.ready().isParticipant)
        vm.toggleHeart()
        assertEquals(listOf("kid1" to true), words.hearts)
        words.words.value = mapOf("s1" to word(hearts = setOf("kid1", "adult2")))
        assertTrue(vm.ready().hearted)
        assertEquals("kid1", vm.ready().hearts.first().id)
        vm.toggleHeart()
        assertEquals("kid1" to false, words.hearts.last())
    }

    @Test
    fun `no heart without a word, and a step that is gone is missing`() = runTest {
        val words = FakeWords()
        val vm = model("kid1", words)
        vm.toggleHeart()
        assertTrue(words.hearts.isEmpty())
        assertNull((vm.ready().word))
        val gone = model("adult1", words, trips = FakeTrips())
        assertEquals(StepWordUiState.Missing, gone.uiState.value)
    }
}
