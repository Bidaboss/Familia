package fr.familia.app.feature.trips.word

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.data.FilmPublication
import fr.familia.app.core.data.TripFilm
import fr.familia.app.core.data.TripFilmRepository
import fr.familia.app.core.data.TripFilms
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.PhotoFilmMaker
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Film de la journée (récit 108, PRD 156 and 157). Fictional first names only. */
@OptIn(ExperimentalCoroutinesApi::class)
class StepDayFilmTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private val trip = Trip("t1", "Corse", day(10), day(20), listOf("lou"), listOf("lou", "sam"), "lou", "lou")
    private val lou = Member("lou", "Lou", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    private var slot = 0

    /** A photo of step [stepId] added on [d] July at [hour] in Paris. */
    private fun photo(d: Int, stepId: String? = "s1", hour: Int = 12) = AlbumPhoto(
        "trips/t1", "lou", "Lou", ++slot,
        day(d).atTime(LocalTime.of(hour, 0)).atZone(ZoneId.of("Europe/Paris")).toInstant(),
        stepId = stepId,
    )

    private fun film(d: Int, stepId: String = "s1") = TripFilm(TripFilms.id(stepId, day(d)), "sam", "Sam", stepId, day(d), 5, 2, null)

    @Test
    fun `one film a step and a day, named as the rules ask`() {
        assertEquals("s1_20270712", TripFilms.id("s1", day(12)))
        assertEquals("trips/t1/films/lou/s1_20270712.mp4", TripFilms.path("t1", "lou", "s1_20270712"))
    }

    @Test
    fun `the day to publish is the latest of the trip with two photos and no film yet`() {
        val photos = listOf(photo(12), photo(12), photo(13), photo(13), photo(14))
        assertEquals(day(13), dayToPublish(photos, emptyList(), "s1", trip, day(14)))
        // The 13th is published: the 12th is left.
        assertEquals(day(12), dayToPublish(photos, listOf(film(13)), "s1", trip, day(14)))
        // A film of another step does not count.
        assertEquals(day(13), dayToPublish(photos, listOf(film(13, "s2")), "s1", trip, day(14)))
        assertNull(dayToPublish(photos, listOf(film(12), film(13)), "s1", trip, day(14)))
    }

    @Test
    fun `never a day out of the trip or still to come`() {
        val photos = listOf(photo(9), photo(9), photo(21), photo(21))
        assertNull(dayToPublish(photos, emptyList(), "s1", trip, day(22)))
        assertNull(dayToPublish(listOf(photo(12), photo(12)), emptyList(), "s1", trip, day(11)))
    }

    @Test
    fun `a photo of late evening counts for its day in Paris`() {
        val photos = listOf(photo(12, hour = 23), photo(12, hour = 8))
        assertEquals(2, dayFilmPhotos(photos, day(12)).size)
    }

    @Test
    fun `the reminder comes the next day, discreetly, then stops`() {
        assertFalse(remindsDayFilm(day(12), day(12)))
        assertTrue(remindsDayFilm(day(12), day(13)))
        assertTrue(remindsDayFilm(day(12), day(14)))
        assertFalse(remindsDayFilm(day(12), day(15)))
        assertFalse(remindsDayFilm(null, day(13)))
    }

    @Test
    fun `a film lasts 2,5 s a photo, in whole seconds`() {
        assertEquals(5, dayFilmSeconds(2))
        assertEquals(8, dayFilmSeconds(3))
        assertEquals(TripFilms.MAX_SECONDS, dayFilmSeconds(500))
    }

    private class FakeStorage : PhotoStorage {
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String): String = "https://example.com/$path"
        override suspend fun delete(path: String) = Unit
    }

    private class FakeMaker(private val bytes: Int = 1000, private val fail: Boolean = false) : PhotoFilmMaker {
        var count = 0
        var style: FilmStyle? = null
        override suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File, style: FilmStyle) {
            if (fail) throw IOException("no encoder")
            count = photoUrls.size
            this.style = style
            output.parentFile?.mkdirs()
            output.writeBytes(ByteArray(bytes))
        }
    }

    private class FakeFilms(private val answer: FilmPublication = FilmPublication.Published) : TripFilmRepository {
        val sent = mutableListOf<Triple<LocalDate, Int, Int>>()
        override fun observe(tripId: String): Flow<List<TripFilm>> = flowOf(emptyList())
        override suspend fun publish(tripId: String, me: Member, stepId: String, day: LocalDate, file: File, seconds: Int, photoCount: Int): FilmPublication {
            sent += Triple(day, seconds, photoCount)
            return answer
        }
        override suspend fun playUrl(tripId: String, film: TripFilm): String? = null
    }

    @Test
    fun `publishing makes the film of that day only, sends it, and leaves no file on the phone`() = runTest {
        val maker = FakeMaker()
        val films = FakeFilms()
        val dir = folder.newFolder("films")
        val vm = DayFilmPublisher(FakeStorage(), maker, films, "t1", "s1", dir)
        vm.publish(lou, day(12), listOf(photo(12), photo(12), photo(12), photo(13)))
        assertEquals(DayFilmState.Done(day(12), alreadyThere = false), vm.uiState.value)
        assertEquals(listOf(Triple(day(12), 8, 3)), films.sent)
        assertEquals(FilmStyle.Trip, maker.style)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `someone was first: their film stays and it is said`() = runTest {
        val vm = DayFilmPublisher(FakeStorage(), FakeMaker(), FakeFilms(FilmPublication.AlreadyThere), "t1", "s1", folder.newFolder("films"))
        vm.publish(lou, day(12), listOf(photo(12), photo(12)))
        assertEquals(DayFilmState.Done(day(12), alreadyThere = true), vm.uiState.value)
    }

    @Test
    fun `a film over 10 Mo is not sent, a failure is said, one photo is not enough`() = runTest {
        val films = FakeFilms()
        val big = DayFilmPublisher(FakeStorage(), FakeMaker(bytes = (TripFilms.MAX_BYTES + 1).toInt()), films, "t1", "s1", folder.newFolder("a"))
        big.publish(lou, day(12), listOf(photo(12), photo(12)))
        assertEquals(DayFilmState.Failed(tooBig = true), big.uiState.value)
        assertTrue(films.sent.isEmpty())
        val failing = DayFilmPublisher(FakeStorage(), FakeMaker(fail = true), films, "t1", "s1", folder.newFolder("b"))
        failing.publish(lou, day(12), listOf(photo(12)))
        assertEquals(DayFilmState.Idle, failing.uiState.value)
        failing.publish(lou, day(12), listOf(photo(12), photo(12)))
        assertEquals(DayFilmState.Failed(), failing.uiState.value)
    }
}
