package fr.familia.app.feature.events.edit

import fr.familia.app.core.data.EventSeriesRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventSeries
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.model.RepeatEnd
import fr.familia.app.core.model.RepeatFreq
import fr.familia.app.core.model.SeriesRule
import fr.familia.app.core.ui.Confirmation
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Événements qui reviennent » in the event form (PRD, récit 54). */
class EventEditSeriesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSeries(initial: List<EventSeries> = emptyList()) : EventSeriesRepository {
        val all = initial.associateBy { it.id }.toMutableMap()
        val saved = mutableListOf<Pair<EventSeries, Boolean>>()
        val deleted = mutableListOf<String>()
        override suspend fun getSeries(id: String): EventSeries? = all[id]
        override fun newSeriesId(): String = "s-new"
        override suspend fun saveSeries(series: EventSeries, isNew: Boolean) {
            saved += series to isNew
            all[series.id] = series
        }
        override suspend fun deleteSeries(id: String) {
            deleted += id
            all.remove(id)
        }
    }

    private val start = LocalDate.of(2027, 1, 5) // A Tuesday.
    private val today = LocalDate.of(2027, 1, 10)
    private val weekly = EventSeries(
        "s1", "Piscine", null, null, null, EventTarget.ForFamily, "adult1", "Adult1",
        SeriesRule(RepeatFreq.Weekly, start = start),
    )

    private fun occurrence(day: LocalDate): Event =
        TestFamily.event("s1_$day", TestFamily.adult, date = day).copy(title = "Piscine", seriesId = "s1", occurrence = day)

    private fun vm(events: FakeEvents, series: FakeSeries, eventId: String? = null, recurring: Boolean = true) =
        EventEditViewModel(events, FakeMembers(), "adult1", eventId, series = series, recurring = recurring, today = { today })

    private fun EventEditViewModel.editing() = uiState.value as EventEditUiState.Editing

    @Test
    fun `« Se répète » shows on a new event only with the switch on`() {
        assertTrue(vm(FakeEvents(), FakeSeries()).editing().showRepeat)
        assertFalse(vm(FakeEvents(), FakeSeries(), recurring = false).editing().showRepeat)
    }

    @Test
    fun `a new event that repeats writes its series, not an event`() {
        val events = FakeEvents()
        val series = FakeSeries()
        val viewModel = vm(events, series)
        viewModel.update { it.copy(title = " Piscine ", date = start, repeat = RepeatChoice.Biweekly, end = RepeatEnd.After(6)) }
        viewModel.save()
        assertEquals(EventEditUiState.Done(Confirmation.Saved), viewModel.uiState.value)
        assertTrue(events.saved.isEmpty())
        val (created, isNew) = series.saved.single()
        assertTrue(isNew)
        assertEquals("Piscine", created.title)
        assertEquals(SeriesRule(RepeatFreq.Weekly, 2, null, start, null, 6), created.rule)
        assertEquals("adult1", created.authorId)
    }

    @Test
    fun `an end before the first day or a wrong number of times blocks saving`() {
        val viewModel = vm(FakeEvents(), FakeSeries())
        viewModel.update { it.copy(title = "Piscine", date = start, repeat = RepeatChoice.Weekly, end = RepeatEnd.OnDay(start.minusDays(1))) }
        viewModel.save()
        assertTrue(EventFormError.RepeatEndInvalid in viewModel.editing().errors)
        viewModel.update { it.copy(end = RepeatEnd.After(1)) }
        assertTrue(EventFormError.RepeatEndInvalid in viewModel.editing().errors)
        viewModel.update { it.copy(end = RepeatEnd.After(2)) }
        assertFalse(EventFormError.RepeatEndInvalid in viewModel.editing().errors)
    }

    @Test
    fun `one of a series shows how it repeats, and asks before saving`() {
        val day = start.plusWeeks(1)
        val viewModel = vm(FakeEvents(listOf(occurrence(day))), FakeSeries(listOf(weekly)), eventId = "s1_$day")
        val state = viewModel.editing()
        assertTrue(state.isOccurrence)
        assertEquals(RepeatChoice.Weekly, state.form.repeat)
        assertEquals(RepeatEnd.Never, state.form.end)
    }

    @Test
    fun `« Cette fois seulement » changes that event alone, kept in its series`() {
        val day = start.plusWeeks(1)
        val events = FakeEvents(listOf(occurrence(day)))
        val series = FakeSeries(listOf(weekly))
        val viewModel = vm(events, series, eventId = "s1_$day")
        viewModel.update { it.copy(place = "Bassin nord") }
        viewModel.save(SeriesScope.ThisOnly)
        val (event, isNew) = events.saved.single()
        assertFalse(isNew)
        assertEquals("s1", event.seriesId)
        assertEquals(day, event.occurrence)
        assertTrue(series.saved.isEmpty())
    }

    @Test
    fun `« Celle-ci et les suivantes » with the same rhythm changes the texts from that day`() {
        val day = start.plusWeeks(2)
        val series = FakeSeries(listOf(weekly))
        val viewModel = vm(FakeEvents(listOf(occurrence(day))), series, eventId = "s1_$day")
        viewModel.update { it.copy(place = "Bassin nord") }
        viewModel.save(SeriesScope.ThisAndFollowing)
        val (saved, isNew) = series.saved.single()
        assertFalse(isNew)
        assertEquals("Bassin nord", saved.place)
        assertEquals(day, saved.applyFrom)
        assertEquals(weekly.rule, saved.rule)
    }

    @Test
    fun `a new rhythm on the next one keeps the same series`() {
        val day = start.plusWeeks(1) // The 12th: nothing left to come before it.
        val series = FakeSeries(listOf(weekly))
        val viewModel = vm(FakeEvents(listOf(occurrence(day))), series, eventId = "s1_$day")
        viewModel.update { it.copy(repeat = RepeatChoice.Biweekly) }
        viewModel.save(SeriesScope.ThisAndFollowing)
        val saved = series.saved.single().first
        assertEquals("s1", saved.id)
        assertEquals(SeriesRule(RepeatFreq.Weekly, 2, null, day), saved.rule)
        assertNull(saved.applyFrom)
    }

    @Test
    fun `a new rhythm later on ends the series the day before and starts a new one`() {
        val day = start.plusWeeks(3) // The 26th: the 12th and the 19th are still to come.
        val series = FakeSeries(listOf(weekly))
        val viewModel = vm(FakeEvents(listOf(occurrence(day))), series, eventId = "s1_$day")
        viewModel.update { it.copy(repeat = RepeatChoice.Biweekly) }
        viewModel.save(SeriesScope.ThisAndFollowing)
        val (created, createdIsNew) = series.saved[0]
        assertTrue(createdIsNew)
        assertEquals("s-new", created.id)
        assertEquals(day, created.rule.start)
        val (ended, _) = series.saved[1]
        assertEquals("s1", ended.id)
        assertEquals(day.minusDays(1), ended.rule.until)
    }

    @Test
    fun `deleting « Cette fois seulement » takes the day out of the series`() {
        val day = start.plusWeeks(2)
        val events = FakeEvents(listOf(occurrence(day)))
        val series = FakeSeries(listOf(weekly))
        val viewModel = vm(events, series, eventId = "s1_$day")
        viewModel.delete(SeriesScope.ThisOnly)
        assertEquals(EventEditUiState.Done(Confirmation.Deleted), viewModel.uiState.value)
        assertEquals(listOf(day), series.saved.single().first.exdates)
        assertTrue(events.deleted.isEmpty())
    }

    @Test
    fun `deleting « Celle-ci et les suivantes » ends the series the day before`() {
        val day = start.plusWeeks(2)
        val series = FakeSeries(listOf(weekly))
        vm(FakeEvents(listOf(occurrence(day))), series, eventId = "s1_$day").delete(SeriesScope.ThisAndFollowing)
        val rule = series.saved.single().first.rule
        assertEquals(day.minusDays(1), rule.until)
        assertNull(rule.count)
    }

    @Test
    fun `deleting from the first day deletes the whole series`() {
        val series = FakeSeries(listOf(weekly))
        vm(FakeEvents(listOf(occurrence(start))), series, eventId = "s1_$start").delete(SeriesScope.ThisAndFollowing)
        assertEquals(listOf("s1"), series.deleted)
    }

    @Test
    fun `with the switch off, one of a series still asks, without « Se répète »`() {
        val day = start.plusWeeks(1)
        val state = vm(FakeEvents(listOf(occurrence(day))), FakeSeries(listOf(weekly)), eventId = "s1_$day", recurring = false).editing()
        assertTrue(state.isOccurrence)
        assertFalse(state.showRepeat)
    }

    @Test
    fun `an event whose series is gone is a plain event`() {
        val day = start.plusWeeks(1)
        val events = FakeEvents(listOf(occurrence(day)))
        val viewModel = vm(events, FakeSeries(), eventId = "s1_$day")
        assertFalse(viewModel.editing().isOccurrence)
        viewModel.delete()
        assertEquals(listOf("s1_$day"), events.deleted)
    }
}
