package fr.familia.app.feature.month

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MonthSlideshowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAlbums(initial: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = photos.map { list -> list.filter { it.folder == folder } }
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    private val july = YearMonth.of(2027, 7)

    private fun photo(folder: String, slot: Int, at: String) = AlbumPhoto(folder, "adult2", "Adult2", slot, Instant.parse(at))

    @Test
    fun `the slideshow comes on the last Sunday of the month, until its end`() {
        assertEquals(LocalDate.of(2026, 11, 29), lastSundayOf(YearMonth.of(2026, 11)))
        assertFalse(isSlideshowTime(LocalDate.of(2026, 11, 28)))
        assertTrue(isSlideshowTime(LocalDate.of(2026, 11, 29)))
        assertTrue(isSlideshowTime(LocalDate.of(2026, 11, 30)))
        assertFalse(isSlideshowTime(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `the recap of the last Sunday opens the slideshow of its month, even touched later`() {
        assertEquals(YearMonth.of(2026, 11), slideshowMonthOf("2026-11-29"))
        assertNull(slideshowMonthOf("2026-11-22"))
        assertNull(slideshowMonthOf("pas une date"))
    }

    @Test
    fun `the birthdays of the month, by day`() {
        val family = TestFamily.family.copy(
            members = listOf(
                TestFamily.adult.copy(birthDate = LocalDate.of(1980, 7, 20)),
                TestFamily.kid.copy(birthDate = LocalDate.of(2015, 7, 3)),
                TestFamily.otherAdult.copy(birthDate = LocalDate.of(1982, 8, 1)),
                TestFamily.admin,
            ),
        )
        assertEquals(listOf("kid1", "adult1"), birthdaysOf(family, july).map { it.id })
    }

    @Test
    fun `each one only sees the photos they already saw`() = runTest {
        val day = LocalDate.of(2027, 7, 14)
        val events = FakeEvents(
            listOf(
                TestFamily.event("e1", TestFamily.adult, date = day),
                // For the other household: kid1 is not concerned.
                TestFamily.event("e2", TestFamily.otherAdult, EventTarget.ForHousehold("h2"), date = day),
                TestFamily.event("e3", TestFamily.adult, date = LocalDate.of(2027, 8, 2)),
            ),
        )
        val trips = FakeTrips(listOf(TestTrips.trip("t1"), TestTrips.trip("t2", readers = listOf("adult1", "adult2"))))
        val albums = FakeAlbums(
            listOf(
                photo("events/e1", 1, "2027-07-14T18:00:00Z"),
                photo("events/e2", 1, "2027-07-14T18:00:00Z"),
                photo("events/e3", 1, "2027-08-02T18:00:00Z"),
                photo("trips/t1", 1, "2027-07-12T09:00:00Z"),
                // Added in August, so in the slideshow of August.
                photo("trips/t1", 2, "2027-08-03T09:00:00Z"),
                photo("trips/t2", 1, "2027-07-12T09:00:00Z"),
            ),
        )
        val vm = MonthSlideshowViewModel(trips, events, albums, FakeMembers(), "kid1", july, ZoneOffset.UTC)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("events/e1" to 1, "trips/t1" to 1), state.photos.map { it.folder to it.slot })
    }

    @Test
    fun `a photo added later shows at once`() = runTest {
        val albums = FakeAlbums(emptyList())
        val vm = MonthSlideshowViewModel(FakeTrips(listOf(TestTrips.trip("t1"))), FakeEvents(), albums, FakeMembers(), "adult1", july, ZoneOffset.UTC)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.photos.isEmpty())

        albums.photos.value = listOf(photo("trips/t1", 3, "2027-07-15T09:00:00Z"))

        assertEquals(1, vm.uiState.value.photos.size)
    }

    private class FakeTree(private val challenge: Flow<MonthChallenge?>) : TreeRepository {
        override fun observeTree(): Flow<FamilyTree> = MutableStateFlow(FamilyTree())
        override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = challenge
    }

    private val summer = MonthChallenge(july, "15 photos de l'été", "☀️", target = 15, progress = 9, achieved = false)

    @Test
    fun `the challenge of the month shows with the photos when the tree is on`() = runTest {
        val vm = MonthSlideshowViewModel(FakeTrips(), FakeEvents(), FakeAlbums(emptyList()), FakeMembers(), "adult1", july, ZoneOffset.UTC, tree = FakeTree(MutableStateFlow(summer)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(summer, vm.uiState.value.challenge)
    }

    @Test
    fun `without the tree, or when the challenge cannot load, the slideshow still shows`() = runTest {
        val off = MonthSlideshowViewModel(FakeTrips(), FakeEvents(), FakeAlbums(emptyList()), FakeMembers(), "adult1", july, ZoneOffset.UTC)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { off.uiState.collect {} }
        assertNull(off.uiState.value.challenge)
        assertFalse(off.uiState.value.loading)

        val broken = FakeTree(flow { throw IllegalStateException("offline") })
        val vm = MonthSlideshowViewModel(FakeTrips(), FakeEvents(), FakeAlbums(emptyList()), FakeMembers(), "adult1", july, ZoneOffset.UTC, tree = broken)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.challenge)
        assertFalse(vm.uiState.value.loading)
    }
}
