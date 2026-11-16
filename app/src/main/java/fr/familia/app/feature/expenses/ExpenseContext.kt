package fr.familia.app.feature.expenses

import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What the accounts need to know of their container, a trip or an event (PRODUIT-57): its title,
 * who created the book (and so changes the shares, corrects everything and closes), and who shares
 * the costs. The adults among [participantIds] read and write the accounts, like the rules say.
 */
data class ExpenseContext(
    val title: String,
    val creatorId: String,
    val participantIds: Set<String>,
)

/** Where the accounts read their container. Null when it is gone. */
interface ExpenseContextSource {
    val container: ExpenseContainer

    fun observe(): Flow<ExpenseContext?>

    suspend fun get(): ExpenseContext? = observe().first()
}

/** A trip: its participants, and its creator. */
class TripExpenseContext(private val trips: TripRepository, tripId: String) : ExpenseContextSource {
    override val container = ExpenseContainer(ExpenseContainerType.Trip, tripId)

    override fun observe(): Flow<ExpenseContext?> = trips.observeTrip(container.id).map { trip ->
        trip?.let { ExpenseContext(it.title, it.creatorId, it.participantIds.toSet()) }
    }

    override suspend fun get(): ExpenseContext? =
        trips.getTrip(container.id)?.let { ExpenseContext(it.title, it.creatorId, it.participantIds.toSet()) }
}

/**
 * An event, a weekend or a meal: those who said « Je viens », children included (their share goes
 * to their household), and its author. « Peut-être » does not share the costs.
 */
class EventExpenseContext(
    private val events: EventRepository,
    private val rsvps: RsvpRepository,
    eventId: String,
) : ExpenseContextSource {
    override val container = ExpenseContainer(ExpenseContainerType.Event, eventId)

    override fun observe(): Flow<ExpenseContext?> =
        combine(events.observeEvent(container.id), rsvps.observeRsvps(container.id)) { event, answers -> eventContext(event, answers) }
}

/** The context of [event] with its [answers]; null once the event is gone. */
fun eventContext(event: Event?, answers: List<Rsvp>): ExpenseContext? {
    event ?: return null
    val coming = answers.filter { it.status == RsvpStatus.Yes }.map { it.memberId }.toSet()
    return ExpenseContext(event.title, event.authorId, coming + event.authorId)
}

/** The source of [container], or null when the app has no data (preview, no Firebase). */
fun expenseContextSource(
    container: ExpenseContainer,
    trips: TripRepository?,
    events: EventRepository?,
    rsvps: RsvpRepository?,
): ExpenseContextSource? = when (container.type) {
    ExpenseContainerType.Trip -> trips?.let { TripExpenseContext(it, container.id) }
    ExpenseContainerType.Event -> if (events != null && rsvps != null) EventExpenseContext(events, rsvps, container.id) else null
}
