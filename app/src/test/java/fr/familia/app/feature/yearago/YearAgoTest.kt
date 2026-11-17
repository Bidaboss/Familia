package fr.familia.app.feature.yearago

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

class YearAgoTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2027, 11, 21)

    /** 21/11/2026 at [hour] in Paris (UTC+1 in November). */
    private fun lastYear(hour: Int): Instant = Instant.parse("2026-11-21T%02d:00:00Z".format(hour - 1))

    /** 21/11 of [year] at noon in Paris. */
    private fun sameDay(year: Int): Instant = Instant.parse("$year-11-21T11:00:00Z")

    private fun news(id: String, at: Instant, photos: Int = 1, reactions: Int = 0, hiddenBy: List<String> = emptyList(), challenge: String? = null) = GoodNews(
        id = id, text = "Nouvelle $id", photoIds = (1..photos).map { "p$it" },
        authorId = "camille", authorName = "Camille", forMemberId = "camille", householdId = "h1",
        reactions = (1..reactions).associate { "m$it" to Reaction.Heart }, hiddenByIds = hiddenBy,
        createdAt = at, challengeMonth = challenge,
    )

    private val trip = Trip("t1", "Lisbonne", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 23), listOf("camille"), listOf("camille", "ines"), "camille", "Camille")
    /** A photo sent once back home. */
    private val createdAtLater = Instant.parse("2026-12-02T10:00:00Z")
    private val lisbon = MemoryAlbum(MemoryAlbum.Kind.Trip, "t1", "Lisbonne")
    private fun photo(slot: Int, at: Instant?, takenAt: Instant? = null, folder: String = "trips/t1") =
        AlbumPhoto(folder, "camille", "Camille", slot, at, takenAt)

    @Test
    fun `the same day, one to ten years back, in Paris`() {
        val bounds = YearAgo.bounds(today)
        assertEquals(10, bounds.size)
        assertEquals(Instant.parse("2026-11-20T23:00:00Z") to Instant.parse("2026-11-21T23:00:00Z"), bounds.first())
        assertEquals(LocalDate.of(2017, 11, 21), YearAgo.days(today).last())
        assertEquals(LocalDate.of(2027, 2, 28), YearAgo.dayOf(LocalDate.of(2028, 2, 29)))
        assertEquals(3, YearAgo.yearsAgo(today, LocalDate.of(2024, 11, 21)))
        assertNull(YearAgo.yearsAgo(today, LocalDate.of(2016, 11, 21)))
        assertNull(YearAgo.yearsAgo(today, LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `a good news with a photo first, then the most loved`() {
        val list = listOf(
            news("text", lastYear(9), photos = 0, reactions = 9),
            news("loved", lastYear(10), reactions = 3),
            news("other", lastYear(8)),
            news("dayBefore", lastYear(1).minusSeconds(7200), reactions = 9),
        )
        assertEquals("news-loved", YearAgo.pick(today, "ines", list, emptyList(), emptySet())?.key)
    }

    @Test
    fun `a good news of three years ago says so`() {
        val memory = YearAgo.pick(today, "ines", listOf(news("old", sameDay(2024))), emptyList(), emptySet())
        assertEquals("news-old", memory?.key)
        assertEquals(3, memory?.years)
    }

    @Test
    fun `a good news one hid, a challenge or birthday news, a souvenir set aside and one taken away stay out`() {
        val list = listOf(
            news("hidden", lastYear(9), hiddenBy = listOf("ines")),
            news("challenge", lastYear(9), photos = 0, challenge = "2026-11"),
            news("birthday-2026-camille", lastYear(9), photos = 0).copy(birthdayOf = "camille"),
            news("aside", lastYear(10)),
            news("away", lastYear(11)),
        )
        assertNull(YearAgo.pick(today, "ines", list, emptyList(), setOf("news-aside"), hidden = setOf("news-away")))
    }

    @Test
    fun `without a good news, a photo taken that day, even if sent later`() {
        val photos = listOf(
            lisbon to photo(1, createdAtLater, takenAt = lastYear(15)),
            lisbon to photo(2, lastYear(11), takenAt = Instant.parse("2026-11-19T10:00:00Z")),
            lisbon to photo(3, lastYear(1).minusSeconds(7200)),
        )
        val memory = YearAgo.pick(today, "ines", emptyList(), photos, emptySet())
        assertEquals("trip-t1-camille_1", memory?.key)
        assertNull(YearAgo.pick(today, "ines", emptyList(), photos, setOf("trip-t1-camille_1")))
    }

    @Test
    fun `the photo changes with the day, never with the order of the albums`() {
        val photos = (1..6).map { lisbon to photo(it, lastYear(10 + it)) }
        val one = YearAgo.pick(today, "ines", emptyList(), photos, emptySet())
        assertEquals(one, YearAgo.pick(today, "ines", emptyList(), photos.reversed(), emptySet()))
        val days = (0L..20L).mapNotNull { n ->
            val day = today.plusDays(n)
            val shifted = photos.map { (album, p) -> album to p.copy(createdAt = day.minusYears(1).atTime(12, 0).atZone(YearAgo.ZONE).toInstant()) }
            YearAgo.pick(day, "ines", emptyList(), shifted, emptySet())?.key
        }
        assertTrue(days.toSet().size > 1)
    }

    @Test
    fun `the souvenir of the morning stays all day, while it is still there`() {
        val list = listOf(news("best", lastYear(10), reactions = 5))
        val photos = listOf(lisbon to photo(1, lastYear(12)))
        assertEquals("trip-t1-camille_1", YearAgo.pick(today, "ines", list, photos, emptySet(), kept = "trip-t1-camille_1")?.key)
        assertEquals("news-best", YearAgo.pick(today, "ines", list, emptyList(), emptySet(), kept = "trip-t1-camille_1")?.key)
        assertEquals("news-best", YearAgo.pick(today, "ines", list, photos, setOf("trip-t1-camille_1"), kept = "trip-t1-camille_1")?.key)
    }

    @Test
    fun `only the trips under way on one of those days can have its photos`() {
        val later = trip.copy(id = "t2", startDate = LocalDate.of(2026, 11, 22))
        val older = trip.copy(id = "t3", startDate = LocalDate.of(2021, 11, 20), endDate = LocalDate.of(2021, 11, 22))
        assertEquals(listOf("t1", "t3"), YearAgo.tripsOfThoseDays(listOf(trip, later, older), today).map { it.id })
    }

    @Test
    fun `only the events of those days that concern me, as in Albums`() {
        val family = TestFamily.family
        val events = listOf(
            TestFamily.event("all", TestFamily.adult, date = LocalDate.of(2025, 11, 21)),
            TestFamily.event("theirs", TestFamily.otherAdult, EventTarget.ForHousehold("h2"), LocalDate.of(2026, 11, 21)),
            TestFamily.event("otherDay", TestFamily.adult, date = LocalDate.of(2026, 11, 20)),
        )
        assertEquals(listOf("all"), YearAgo.eventsOfThoseDays(events, today, family, "kid1").map { it.id })
        assertEquals(listOf("all", "theirs"), YearAgo.eventsOfThoseDays(events, today, family, "adult2").map { it.id })
    }

    private class Store : DismissedMemories {
        val saved = mutableMapOf<String, Set<String>>()
        var todayValue: String? = null
        override fun load(memberId: String) = saved[memberId].orEmpty()
        override fun save(memberId: String, keys: Set<String>) { saved[memberId] = keys }
        override fun loadToday(memberId: String): String? = todayValue
        override fun saveToday(memberId: String, value: String) { todayValue = value }
    }

    private class News(private val list: List<GoodNews>) : GoodNewsRepository {
        override fun observeBetween(from: Instant, to: Instant): Flow<List<GoodNews>> = flowOf(list.filter { it.createdAt!! >= from && it.createdAt!! < to })
        override fun observeLatest(count: Int): Flow<List<GoodNews>> = flowOf(emptyList())
        override fun observePost(id: String): Flow<GoodNews?> = flowOf(null)
        override fun newId(): String = "x"
        override suspend fun publish(news: GoodNews) = Unit
        override suspend fun edit(id: String, text: String, mentionIds: List<String>) = Unit
        override suspend fun react(id: String, memberId: String, reaction: Reaction?) = Unit
        override suspend fun setHidden(id: String, memberId: String, hidden: Boolean) = Unit
        override suspend fun remove(id: String) = Unit
    }

    private class Albums(private val photos: List<AlbumPhoto>) : AlbumPhotoRepository {
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = flowOf(photos.filter { it.folder == folder })
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    @Test
    fun `Ne plus me montrer ce souvenir shows the next one and is remembered for me`() = runTest {
        val store = Store()
        val repo = News(listOf(news("a", lastYear(10), reactions = 2), news("b", lastYear(11))))
        val vm = YearAgoViewModel(repo, null, null, "ines", today, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        assertEquals("news-a", vm.memory.value?.key)
        vm.dismiss(vm.memory.value!!)
        assertEquals("news-b", vm.memory.value?.key)
        assertEquals(setOf("news-a"), store.saved["ines"])
        val again = YearAgoViewModel(repo, null, null, "ines", today, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { again.memory.collect {} }
        assertEquals("news-b", again.memory.value?.key)
    }

    @Test
    fun `the souvenir shown this morning is kept when the card comes back`() = runTest {
        val store = Store().apply { todayValue = "$today|news-b" }
        val repo = News(listOf(news("a", lastYear(10), reactions = 2), news("b", lastYear(11))))
        val vm = YearAgoViewModel(repo, null, null, "ines", today, store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        assertEquals("news-b", vm.memory.value?.key)

        val yesterday = Store().apply { todayValue = "${today.minusDays(1)}|news-b" }
        val next = YearAgoViewModel(repo, null, null, "ines", today, yesterday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { next.memory.collect {} }
        assertEquals("news-a", next.memory.value?.key)
        assertEquals("$today|news-a", yesterday.todayValue)
    }

    @Test
    fun `a photo of the album of an event of that day, for those it concerns`() = runTest {
        val event = TestFamily.event("e1", TestFamily.adult, date = LocalDate.of(2025, 11, 21))
        val albums = Albums(listOf(photo(1, sameDay(2025), folder = "events/e1")))
        val vm = YearAgoViewModel(null, null, albums, "kid1", today, Store(), events = FakeEvents(listOf(event)), members = FakeMembers())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        val memory = vm.memory.value as YearAgoMemory.Photo
        assertEquals(MemoryAlbum(MemoryAlbum.Kind.Event, "e1", "Repas e1"), memory.album)
        assertEquals(2, memory.years)
        assertEquals("event-e1-camille_1", memory.key)
    }

    private class Config(hidden: List<String> = emptyList(), private val refuse: Boolean = false) : AppConfigRepository {
        val config = MutableStateFlow(AppConfig(null, null, memoriesHidden = hidden))
        override fun observeAppConfig(): Flow<AppConfig> = config
        override suspend fun fetchAppConfig(): AppConfig = config.value
        override suspend fun setFeature(key: String, enabled: Boolean) = Unit
        override suspend fun setDiscoverOrder(keys: List<String>) = Unit
        override suspend fun hideMemory(key: String) {
            if (refuse) throw IllegalStateException("rules")
            config.value = config.value.copy(memoriesHidden = config.value.memoriesHidden + key)
        }
    }

    private val two = News(listOf(news("a", lastYear(10), reactions = 2), news("b", lastYear(11))))

    @Test
    fun `a souvenir the admin principal took away shows to nobody`() = runTest {
        val vm = YearAgoViewModel(two, null, null, "ines", today, Store(), config = Config(listOf("news-a")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        assertEquals("news-b", vm.memory.value?.key)
    }

    @Test
    fun `Retirer ce souvenir pour tous writes it for the family, and only the admin principal can`() = runTest {
        val config = Config()
        val vm = YearAgoViewModel(two, null, null, "admin", today, Store(), config = config, canHideForAll = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        vm.hideForAll(vm.memory.value!!)
        assertEquals(listOf("news-a"), config.config.value.memoriesHidden)
        assertEquals("news-b", vm.memory.value?.key)

        val other = Config()
        val member = YearAgoViewModel(two, null, null, "ines", today, Store(), config = other)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { member.memory.collect {} }
        member.hideForAll(member.memory.value!!)
        assertTrue(other.config.value.memoriesHidden.isEmpty())
    }

    @Test
    fun `a refusal of the server brings the souvenir back and is said`() = runTest {
        val vm = YearAgoViewModel(two, null, null, "admin", today, Store(), config = Config(refuse = true), canHideForAll = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        vm.hideForAll(vm.memory.value!!)
        assertEquals("news-a", vm.memory.value?.key)
        assertTrue(vm.hideFailed.value)
        vm.hideFailureShown()
        assertFalse(vm.hideFailed.value)
    }

    @Test
    fun `in Voir comme, nothing is set aside`() = runTest {
        val store = Store()
        val vm = YearAgoViewModel(News(listOf(news("a", lastYear(10)))), null, null, "ines", today, store, canDismiss = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        vm.dismiss(vm.memory.value!!)
        assertEquals("news-a", vm.memory.value?.key)
        assertEquals(emptyMap<String, Set<String>>(), store.saved)
    }
}
