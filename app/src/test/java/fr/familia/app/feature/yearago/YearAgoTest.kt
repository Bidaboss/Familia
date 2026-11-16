package fr.familia.app.feature.yearago

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.Trip
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun news(id: String, at: Instant, photos: Int = 1, reactions: Int = 0, hiddenBy: List<String> = emptyList(), challenge: String? = null) = GoodNews(
        id = id, text = "Nouvelle $id", photoIds = (1..photos).map { "p$it" },
        authorId = "camille", authorName = "Camille", forMemberId = "camille", householdId = "h1",
        reactions = (1..reactions).associate { "m$it" to Reaction.Heart }, hiddenByIds = hiddenBy,
        createdAt = at, challengeMonth = challenge,
    )

    private val trip = Trip("t1", "Lisbonne", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 23), listOf("camille"), listOf("camille", "ines"), "camille", "Camille")
    private fun photo(slot: Int, at: Instant?) = AlbumPhoto("trips/t1", "camille", "Camille", slot, at)

    @Test
    fun `the day of last year, in Paris`() {
        val (from, to) = YearAgo.bounds(today)
        assertEquals(Instant.parse("2026-11-20T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-11-21T23:00:00Z"), to)
        assertEquals(LocalDate.of(2027, 2, 28), YearAgo.dayOf(LocalDate.of(2028, 2, 29)))
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
    fun `a good news one hid, a challenge news and a souvenir set aside stay out`() {
        val list = listOf(
            news("hidden", lastYear(9), hiddenBy = listOf("ines")),
            news("challenge", lastYear(9), photos = 0, challenge = "2026-11"),
            news("aside", lastYear(10)),
        )
        assertNull(YearAgo.pick(today, "ines", list, emptyList(), setOf("news-aside")))
    }

    @Test
    fun `without a good news, a photo of a trip taken that day`() {
        val photos = listOf(trip to photo(2, lastYear(15)), trip to photo(1, lastYear(11)), trip to photo(3, lastYear(1).minusSeconds(7200)))
        val memory = YearAgo.pick(today, "ines", emptyList(), photos, emptySet())
        assertEquals("trip-t1-camille_1", memory?.key)
        assertEquals("trip-t1-camille_2", YearAgo.pick(today, "ines", emptyList(), photos, setOf("trip-t1-camille_1"))?.key)
    }

    @Test
    fun `only the trips under way that day can have its photos`() {
        val later = trip.copy(id = "t2", startDate = LocalDate.of(2026, 11, 22))
        assertEquals(listOf("t1"), YearAgo.tripsOfThatDay(listOf(trip, later), today).map { it.id })
    }

    private class Store : DismissedMemories {
        val saved = mutableMapOf<String, Set<String>>()
        override fun load(memberId: String) = saved[memberId].orEmpty()
        override fun save(memberId: String, keys: Set<String>) { saved[memberId] = keys }
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
    fun `in Voir comme, nothing is set aside`() = runTest {
        val store = Store()
        val vm = YearAgoViewModel(News(listOf(news("a", lastYear(10)))), null, null, "ines", today, store, canDismiss = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.memory.collect {} }
        vm.dismiss(vm.memory.value!!)
        assertEquals("news-a", vm.memory.value?.key)
        assertEquals(emptyMap<String, Set<String>>(), store.saved)
    }
}
