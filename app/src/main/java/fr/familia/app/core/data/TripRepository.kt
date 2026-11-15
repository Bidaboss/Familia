package fr.familia.app.core.data

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
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
}

/** Steps by departure: day, then steps without a time first, then time, then place. */
val stepOrder: Comparator<TripStep> =
    compareBy<TripStep>({ it.departDate }, { it.departTime != null }, { it.departTime }, { it.place.lowercase() })

/** Trips by start date, then title. */
val tripOrder: Comparator<Trip> = compareBy<Trip>({ it.startDate }, { it.endDate }, { it.title.lowercase() })
