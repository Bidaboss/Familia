package fr.familia.app.core.data

import fr.familia.app.core.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Access to the planning. Screens never talk to Firestore directly. */
interface EventRepository {
    /** Events from [from] on, by date then time, updated live (and served from the offline cache). */
    fun observeUpcoming(from: LocalDate): Flow<List<Event>>

    /** One event, or null if it was deleted. */
    suspend fun getEvent(id: String): Event?

    /** One event, updated live; null once it is deleted or if it has a malformed shape. */
    fun observeEvent(id: String): Flow<Event?>

    /** The events of [days] (ten at most), for « Il y a N ans » (récit 36). */
    fun observeOnDays(days: List<LocalDate>): Flow<List<Event>> = flowOf(emptyList())

    /** A fresh id for an event that is not saved yet. */
    fun newEventId(): String

    /** Creates the event, or updates it when [isNew] is false (author and creation time never change). */
    suspend fun saveEvent(event: Event, isNew: Boolean)

    suspend fun deleteEvent(id: String)
}

/** Planning order: by date, events without a time first (they last all day), then by time. */
val eventOrder: Comparator<Event> = compareBy<Event>({ it.date }, { it.time != null }, { it.time }, { it.title.lowercase() })
