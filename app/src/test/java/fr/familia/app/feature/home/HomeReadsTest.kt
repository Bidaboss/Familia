package fr.familia.app.feature.home

import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The home screen reads only what it shows (lectures Firestore, 17/11). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeReadsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 21)
    private val me = TestFamily.adult

    /** Records what the home screen asks, and never gives every answer of the family. */
    private class Spy(private val inner: FakeRsvps) : RsvpRepository by inner {
        val asked = mutableListOf<List<String>>()
        var allCalls = 0

        override fun observeAllRsvps(): Flow<Map<String, List<Rsvp>>> {
            allCalls++
            return inner.observeAllRsvps()
        }

        override fun observeAnswersOf(memberId: String, eventIds: List<String>): Flow<Map<String, List<Rsvp>>> {
            asked += eventIds
            return inner.answers.map { all ->
                eventIds.associateWith { id -> all[id].orEmpty().filter { it.memberId == memberId } }.filterValues { it.isNotEmpty() }
            }
        }
    }

    @Test
    fun `only the events of the next 30 days and only my own answers are read`() = runTest {
        val events = FakeEvents(
            listOf(
                TestFamily.event("soon", TestFamily.admin, date = today.plusDays(2)),
                TestFamily.event("answered", TestFamily.admin, date = today.plusDays(3)),
                TestFamily.event("far", TestFamily.admin, date = today.plusDays(45)),
            ),
        )
        val fake = FakeRsvps()
        fake.answers.value = mapOf("answered" to listOf(Rsvp(me.id, RsvpStatus.Yes, me.id)))
        val spy = Spy(fake)
        val vm = HomeViewModel(FakeMembers(), events, me.id, spy, today = flowOf(today))
        vm.uiState.launchIn(backgroundScope)

        val ready = vm.uiState.first { it is HomeUiState.Ready && it.todos.size == 1 } as HomeUiState.Ready
        assertEquals(listOf("soon"), ready.todos.map { (it as HomeTodo.Answer).event.id })
        assertEquals(listOf("soon", "answered"), spy.asked.last())
        assertEquals(0, spy.allCalls)
    }
}
