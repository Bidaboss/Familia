package fr.familia.app.core.data

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.model.TripStep
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Access to the trips (PRD, V3). Screens never talk to Firestore directly. */
interface TripRepository {
    /** The trips [memberId] follows, past ones included, updated live (and from the offline cache). */
    fun observeFollowedTrips(memberId: String): Flow<List<Trip>>

    /** One trip, updated live; null once it is deleted or no longer followed. */
    fun observeTrip(id: String): Flow<Trip?>

    /** The steps of a trip, in [stepOrder], updated live. */
    fun observeSteps(tripId: String): Flow<List<TripStep>>

    suspend fun getTrip(id: String): Trip?

    suspend fun getStep(tripId: String, stepId: String): TripStep?

    fun newTripId(): String

    fun newStepId(tripId: String): String

    /** Creates the trip, or updates it when [isNew] is false (creator and creation time never change). */
    suspend fun saveTrip(trip: Trip, isNew: Boolean)

    /** Deletes the trip; its steps are deleted on the server. */
    suspend fun deleteTrip(id: String)

    /**
     * Creates or updates a step. [remindAt] is when the participants are reminded to say « bien
     * arrivé » (null for no reminder). The arrival is never changed here.
     */
    suspend fun saveStep(tripId: String, step: TripStep, isNew: Boolean, remindAt: Instant?)

    suspend fun deleteStep(tripId: String, stepId: String)

    /** « Bien arrivé » by [me], for everyone of the trip. The reminder is cancelled. */
    suspend fun markArrived(tripId: String, stepId: String, me: Member)

    /** Takes back a « bien arrivé » tapped by mistake. */
    suspend fun clearArrival(tripId: String, stepId: String)

    /**
     * « Prévenir ma famille » (récit 105, règle 43): [me], who takes part in the trip, asks the
     * server to tell everyone who follows it. No position is sent. Refused by the rules within 10
     * minutes of the last one from [me] on this trip: [AlertTooSoonException].
     */
    suspend fun alertFamily(tripId: String, me: Member)

    /** The checklist of a trip (récit 13), in [itemOrder], updated live. */
    fun observeItems(tripId: String): Flow<List<TripItem>>

    fun newItemId(tripId: String): String

    /** Creates a line of the checklist, or updates its label and who is in charge (never whether it is done). */
    suspend fun saveItem(tripId: String, item: TripItem, isNew: Boolean)

    /**
     * « Je m'en charge » / « Je ne peux plus » (récit 62): sets who is in charge of the line, only
     * when it is still [expectedId] (null: free). Touches nothing else. False when someone was faster.
     */
    suspend fun setItemAssignee(tripId: String, itemId: String, expectedId: String?, assignee: Member?): Boolean

    /** Ticks or unticks a line, without touching the rest. */
    suspend fun setItemDone(tripId: String, itemId: String, done: Boolean)

    suspend fun deleteItem(tripId: String, itemId: String)
}

/** « Prévenir ma famille » refused: the last one from this member was less than 10 minutes ago. */
class AlertTooSoonException(cause: Throwable? = null) : Exception(cause)

/** What is left to do first, then by label. */
val itemOrder: Comparator<TripItem> = compareBy<TripItem>({ it.done }, { it.label.lowercase() })

/** Steps by departure: day, then steps without a time first, then time, then place. */
val stepOrder: Comparator<TripStep> =
    compareBy<TripStep>({ it.departDate }, { it.departTime != null }, { it.departTime }, { it.place.lowercase() })

/** Trips by start date, then title. */
val tripOrder: Comparator<Trip> = compareBy<Trip>({ it.startDate }, { it.endDate }, { it.title.lowercase() })
