package fr.familia.app.feature.firststeps

import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.data.MemberPhoto
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import fr.familia.app.feature.goodnews.FakeGoodNews
import fr.familia.app.feature.goodnews.NOW
import fr.familia.app.feature.goodnews.goodNews
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Mes premiers pas » (récit 61). Fictional family: Camille is new, Inès publishes. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstStepsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = LocalDate.of(2026, 11, 21)
    private val none = mapOf(FirstStep.Photo to false, FirstStep.Tastes to false, FirstStep.Reaction to false)
    private val all = none.mapValues { true }

    @Test
    fun `the first day starts the 14 days and shows the three gestures`() {
        val (card, marks) = firstSteps(none, FirstStepsMarks(), day)
        assertEquals(FirstStepsCard.Steps(FirstStep.entries.map { FirstStepLine(it, false) }), card)
        assertEquals(day.toEpochDay(), marks.startDay)
        assertTrue(marks.sawUndone)
    }

    @Test
    fun `gone after 14 days, shown on the 14th`() {
        val start = FirstStepsMarks(startDay = day.toEpochDay())
        assertTrue(firstSteps(none, start, day.plusDays(13)).first is FirstStepsCard.Steps)
        assertNull(firstSteps(none, start, day.plusDays(14)).first)
    }

    @Test
    fun `Plus tard hides it for the day only, Ne plus afficher for good`() {
        val later = FirstStepsMarks(startDay = day.toEpochDay(), snoozedDay = day.toEpochDay())
        assertNull(firstSteps(none, later, day).first)
        assertTrue(firstSteps(none, later, day.plusDays(1)).first is FirstStepsCard.Steps)
        assertNull(firstSteps(none, later.copy(dismissed = true), day.plusDays(1)).first)
    }

    @Test
    fun `Bravo the day the last gesture is done, then nothing`() {
        val seen = firstSteps(none, FirstStepsMarks(), day).second
        val (bravo, marks) = firstSteps(all, seen, day.plusDays(2))
        assertEquals(FirstStepsCard.Bravo, bravo)
        assertNull(firstSteps(all, marks, day.plusDays(3)).first)
    }

    @Test
    fun `a member who had done everything before never sees the card`() {
        assertNull(firstSteps(all, FirstStepsMarks(), day).first)
    }

    @Test
    fun `without Gouts et passions, two gestures`() {
        val two = mapOf(FirstStep.Photo to true, FirstStep.Reaction to false)
        val card = firstSteps(two, FirstStepsMarks(), day).first as FirstStepsCard.Steps
        assertEquals(listOf(FirstStep.Photo, FirstStep.Reaction), card.lines.map { it.step })
    }

    @Test
    fun `each gesture is ticked by itself from what the family sees`() = runTest {
        val photos = FakePhotos()
        val cards = FakeCards()
        val news = FakeGoodNews(listOf(goodNews("n1", INES)))
        val store = MemoryStore()
        val vm = FirstStepsViewModel(photos, cards, news, store, CAMILLE.id, tastesOn = true, clock = { NOW })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.card.collect {} }
        assertEquals(listOf(false, false, false), (vm.card.value as FirstStepsCard.Steps).lines.map { it.done })

        photos.photos.value = mapOf(CAMILLE.id to MemberPhoto(CAMILLE.id, ByteArray(1), 1))
        cards.card.value = MemberCard(CAMILLE.id, hobbies = "La randonnée")
        assertEquals(listOf(true, true, false), (vm.card.value as FirstStepsCard.Steps).lines.map { it.done })

        news.react("n1", CAMILLE.id, Reaction.Heart)
        assertEquals(FirstStepsCard.Bravo, vm.card.value)
        assertEquals(LocalDate.of(2026, 11, 21).toEpochDay(), store.marks?.doneDay)
    }

    @Test
    fun `Plus tard and Ne plus afficher are kept on the phone`() = runTest {
        val store = MemoryStore()
        val vm = FirstStepsViewModel(FakePhotos(), FakeCards(), FakeGoodNews(), store, CAMILLE.id, tastesOn = false, clock = { NOW })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.card.collect {} }
        assertEquals(2, (vm.card.value as FirstStepsCard.Steps).lines.size)
        vm.later()
        assertNull(vm.card.value)
        assertEquals(LocalDate.of(2026, 11, 21).toEpochDay(), store.marks?.snoozedDay)
        vm.dismiss()
        assertTrue(store.marks?.dismissed == true)
    }

    private class MemoryStore : FirstStepsStore {
        var marks: FirstStepsMarks? = null
        override fun load(memberId: String) = marks ?: FirstStepsMarks()
        override fun save(memberId: String, marks: FirstStepsMarks) {
            this.marks = marks
        }
    }

    private class FakePhotos : MemberPhotoRepository {
        val photos = MutableStateFlow<Map<String, MemberPhoto>>(emptyMap())
        override fun observePhotos(): Flow<Map<String, MemberPhoto>> = photos
        override suspend fun savePhoto(memberId: String, jpeg: ByteArray) {
            photos.value = photos.value + (memberId to MemberPhoto(memberId, jpeg, 1))
        }
        override suspend fun removePhoto(memberId: String) {
            photos.value = photos.value - memberId
        }
    }

    private class FakeCards : MemberCardRepository {
        val card = MutableStateFlow<MemberCard?>(null)
        override fun observeCard(memberId: String): Flow<MemberCard?> = card.map { it?.takeIf { c -> c.memberId == memberId } }
        override suspend fun saveCard(card: MemberCard, byId: String) {
            this.card.value = card
        }
    }

    private companion object {
        val CAMILLE = Member("m1", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
        val INES = Member("m2", "Inès", null, null, "h2", MemberProfile.Adult, false, emptyList(), null)
    }
}
