package fr.familia.app.feature.trips.story

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TripStoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWords(initial: Map<String, TripWord>) : TripWordRepository {
        val words = MutableStateFlow(initial)
        val hearts = mutableListOf<Triple<String, String, Boolean>>()
        override fun observeWords(tripId: String): Flow<Map<String, TripWord>> = words
        override fun observeWord(tripId: String, stepId: String): Flow<TripWord?> = words.map { it[stepId] }
        override suspend fun saveWord(tripId: String, stepId: String, text: String, photoIds: List<String>, me: Member, isNew: Boolean) = Unit
        override suspend fun deleteWord(tripId: String, stepId: String) = Unit
        override suspend fun setHeart(tripId: String, stepId: String, memberId: String, hearted: Boolean) { hearts += Triple(stepId, memberId, hearted) }
        override fun comments(tripId: String, stepId: String): EventCommentRepository = error("not under test")
    }

    private class FakeAlbum(initial: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = photos
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    private val first = LocalDate.of(2027, 7, 10)
    private val third = LocalDate.of(2027, 7, 12)
    private val album = listOf(1, 2).map { AlbumPhoto("trips/t1", "adult1", "Adult1", it, Instant.EPOCH) }

    private fun word(step: String, vararg photos: String, hearts: Set<String> = emptySet()) = TripWord(step, "Mot $step", photos.toList(), "adult1", "Adult1", hearts)

    private fun kotlinx.coroutines.test.TestScope.model(words: FakeWords, memberId: String = "adult2", day: LocalDate? = null): TripStoryViewModel {
        val trips = FakeTrips(steps = mapOf("t1" to listOf(TestTrips.step("s1", first), TestTrips.step("s2", third))))
        val vm = TripStoryViewModel(trips, words, FakeAlbum(album), FakeMembers(), memberId, "t1", day, today = LocalDate.of(2027, 7, 30))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun TripStoryViewModel.ready() = uiState.value as TripStoryUiState.Ready

    @Test
    fun `it opens on the last day with a word when no day is asked and the trip is over`() = runTest {
        val vm = model(FakeWords(mapOf("s1" to word("s1"), "s2" to word("s2"))))
        assertEquals(third, vm.ready().day.date)
        assertEquals(0, vm.ready().frameIndex)
    }

    @Test
    fun `the day asked opens first`() = runTest {
        val vm = model(FakeWords(mapOf("s1" to word("s1"), "s2" to word("s2"))), day = first)
        assertEquals(first, vm.ready().day.date)
    }

    @Test
    fun `touching goes on and back, never past the ends, and nothing moves by itself`() = runTest {
        val vm = model(FakeWords(mapOf("s1" to word("s1", "adult1_1", "adult1_2"))))
        // Two photos of the word, then the route.
        assertEquals(3, vm.ready().frames.size)
        vm.previous()
        assertEquals(0, vm.ready().frameIndex)
        vm.next()
        vm.next()
        assertEquals(StoryFrame.End, vm.ready().frame)
        vm.next()
        assertEquals(2, vm.ready().frameIndex)
        vm.previous()
        assertEquals(1, vm.ready().frameIndex)
    }

    @Test
    fun `from the route, the next day and the replay`() = runTest {
        val vm = model(FakeWords(mapOf("s1" to word("s1"), "s2" to word("s2"))), day = first)
        vm.next()
        assertTrue(vm.ready().hasNextDay)
        vm.nextDay()
        assertEquals(third, vm.ready().day.date)
        assertEquals(0, vm.ready().frameIndex)
        vm.next()
        assertFalse(vm.ready().hasNextDay)
        vm.replay()
        assertEquals(0, vm.ready().frameIndex)
    }

    @Test
    fun `the heart goes on the word shown, and again takes it back`() = runTest {
        val words = FakeWords(mapOf("s1" to word("s1")))
        val vm = model(words)
        vm.toggleHeart()
        assertEquals(listOf(Triple("s1", "adult2", true)), words.hearts)
        words.words.value = mapOf("s1" to word("s1", hearts = setOf("adult2")))
        assertTrue(vm.ready().hearted)
        vm.toggleHeart()
        assertEquals(Triple("s1", "adult2", false), words.hearts.last())
    }

    @Test
    fun `with no word at all there is nothing to revisit`() = runTest {
        val vm = model(FakeWords(emptyMap()))
        assertEquals(TripStoryUiState.Missing, vm.uiState.value)
    }
}
