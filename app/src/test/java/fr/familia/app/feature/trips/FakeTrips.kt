package fr.familia.app.feature.trips

import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.itemOrder
import fr.familia.app.core.data.stepOrder
import fr.familia.app.core.data.tripOrder
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.model.TripStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Fictional trips of the test family (see TestFamily): adult1 goes with adult2, everyone follows. */
object TestTrips {
    val everyone = listOf("admin1", "adult1", "adult2", "kid1", "kid2")

    fun trip(
        id: String = "t1",
        creator: String = "adult1",
        participants: List<String> = listOf("adult1", "adult2"),
        readers: List<String> = everyone,
        start: LocalDate = LocalDate.of(2027, 7, 10),
        end: LocalDate = LocalDate.of(2027, 7, 20),
    ) = Trip(id, "Voyage $id", start, end, participants, readers, creator, creator.replaceFirstChar { it.uppercase() })

    fun step(id: String = "s1", day: LocalDate = LocalDate.of(2027, 7, 10), arrival: TripArrival? = null, lat: Double? = null, lng: Double? = null) =
        TripStep(id, StepType.Flight, "Ajaccio", day, LocalTime.of(8, 15), day, LocalTime.of(9, 40), null, arrival, lat, lng)
}

class FakeTrips(
    initial: List<Trip> = listOf(TestTrips.trip()),
    steps: Map<String, List<TripStep>> = emptyMap(),
    private val failWith: Exception? = null,
    items: Map<String, List<TripItem>> = emptyMap(),
) : TripRepository {
    val items = MutableStateFlow(items)
    val savedItems = mutableListOf<Pair<TripItem, Boolean>>()
    val ticked = mutableListOf<Pair<String, Boolean>>()
    val assigned = mutableListOf<Triple<String, String?, String?>>()
    val deletedItems = mutableListOf<String>()
    val trips = MutableStateFlow(initial)
    val steps = MutableStateFlow(steps)
    val savedTrips = mutableListOf<Pair<Trip, Boolean>>()
    val deletedTrips = mutableListOf<String>()
    val savedSteps = mutableListOf<Triple<TripStep, Boolean, Instant?>>()
    val deletedSteps = mutableListOf<String>()
    val arrivals = mutableListOf<Pair<String, String>>()
    val cleared = mutableListOf<String>()

    override fun observeFollowedTrips(memberId: String): Flow<List<Trip>> =
        if (failWith != null) flow { throw failWith } else trips.map { list -> list.filter { memberId in it.readerIds }.sortedWith(tripOrder) }

    override fun observeTrip(id: String): Flow<Trip?> = trips.map { list -> list.firstOrNull { it.id == id } }
    override fun observeSteps(tripId: String): Flow<List<TripStep>> = steps.map { it[tripId].orEmpty().sortedWith(stepOrder) }
    override suspend fun getTrip(id: String): Trip? = trips.value.firstOrNull { it.id == id }
    override suspend fun getStep(tripId: String, stepId: String): TripStep? = steps.value[tripId]?.firstOrNull { it.id == stepId }
    override fun newTripId(): String = "t-new"
    override fun newStepId(tripId: String): String = "s-new"

    override suspend fun saveTrip(trip: Trip, isNew: Boolean) {
        failWith?.let { throw it }
        savedTrips += trip to isNew
    }

    override suspend fun deleteTrip(id: String) {
        failWith?.let { throw it }
        deletedTrips += id
    }

    override suspend fun saveStep(tripId: String, step: TripStep, isNew: Boolean, remindAt: Instant?) {
        failWith?.let { throw it }
        savedSteps += Triple(step, isNew, remindAt)
    }

    override suspend fun deleteStep(tripId: String, stepId: String) {
        failWith?.let { throw it }
        deletedSteps += stepId
    }

    override suspend fun markArrived(tripId: String, stepId: String, me: Member) {
        failWith?.let { throw it }
        arrivals += stepId to me.id
    }

    override suspend fun clearArrival(tripId: String, stepId: String) {
        failWith?.let { throw it }
        cleared += stepId
    }

    override fun observeItems(tripId: String): Flow<List<TripItem>> = items.map { it[tripId].orEmpty().sortedWith(itemOrder) }
    override fun newItemId(tripId: String): String = "i-new"

    override suspend fun saveItem(tripId: String, item: TripItem, isNew: Boolean) {
        failWith?.let { throw it }
        savedItems += item to isNew
    }

    override suspend fun setItemAssignee(tripId: String, itemId: String, expectedId: String?, assignee: Member?): Boolean {
        failWith?.let { throw it }
        assigned += Triple(itemId, expectedId, assignee?.id)
        return true
    }

    override suspend fun setItemDone(tripId: String, itemId: String, done: Boolean) {
        failWith?.let { throw it }
        ticked += itemId to done
    }

    override suspend fun deleteItem(tripId: String, itemId: String) {
        failWith?.let { throw it }
        deletedItems += itemId
    }
}
