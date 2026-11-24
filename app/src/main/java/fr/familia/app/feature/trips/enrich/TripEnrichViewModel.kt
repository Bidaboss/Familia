package fr.familia.app.feature.trips.enrich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TripPlaceRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.place.PlaceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import fr.familia.app.core.place.distanceMeters

/** The key of a line of the preview: a new destination, or a place under a destination. */
fun destinationKey(d: Int): String = "d$d"
fun placeKey(d: Int, p: Int): String = "p$d.$p"

/** Every line of [reading], ticked: the person unticks what they do not want (règle 29). */
fun allKeys(reading: EnrichReading.Read): Set<String> = buildSet {
    reading.destinations.forEachIndexed { d, destination ->
        if (destination.isNew) add(destinationKey(d))
        destination.places.indices.forEach { p -> add(placeKey(d, p)) }
    }
}

/**
 * What is really added from [reading] with [ticked]: a place under a new destination that is not
 * ticked has nowhere to go, so it is left out too.
 */
fun chosenCount(reading: EnrichReading.Read, ticked: Set<String>): Int =
    reading.destinations.withIndex().sumOf { (d, destination) ->
        if (destination.isNew && destinationKey(d) !in ticked) {
            0
        } else {
            (if (destination.isNew) 1 else 0) + destination.places.indices.count { p -> placeKey(d, p) in ticked }
        }
    }

data class EnrichUiState(
    val target: EnrichTarget = EnrichTarget.Trip,
    val answer: String = "",
    val reading: EnrichReading? = null,
    val ticked: Set<String> = emptySet(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** How many lines were added, once done. */
    val added: Int? = null,
)

/** « Enrichir avec une IA » (récit 106): the prompt, the pasted answer, the preview, then the writes. */
class TripEnrichViewModel(
    private val trips: TripRepository?,
    private val places: TripPlaceRepository?,
    private val locator: PlaceLocator?,
    private val tripId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {
    private val _state = MutableStateFlow(EnrichUiState())
    val state: StateFlow<EnrichUiState> = _state.asStateFlow()

    fun setTarget(target: EnrichTarget) {
        if (_state.value.saving) return
        _state.value = EnrichUiState(target = target)
    }

    fun setAnswer(text: String) {
        if (_state.value.saving) return
        // Pasted text past the limit is kept only up to one character over it, to say « trop long ».
        _state.value = _state.value.copy(answer = text.take(TripEnrich.MAX_CHARS + 1), reading = null, ticked = emptySet(), added = null)
    }

    /** The points of the destinations of the trip, read with the answer: a place is placed near its own. */
    private var anchors: Map<String, Pair<Double, Double>> = emptyMap()

    fun read(trip: Trip, steps: List<TripStep>) {
        anchors = steps.mapNotNull { s -> if (s.lat != null && s.lng != null) s.id to (s.lat to s.lng) else null }.toMap()
        val current = _state.value
        val reading = readEnrichAnswer(current.answer, trip, steps, current.target)
        _state.value = current.copy(reading = reading, ticked = (reading as? EnrichReading.Read)?.let(::allKeys).orEmpty(), saveFailed = false)
    }

    fun toggle(key: String) {
        val current = _state.value
        if (current.saving) return
        _state.value = current.copy(ticked = if (key in current.ticked) current.ticked - key else current.ticked + key)
    }

    /** Writes what is ticked: the new stays first, then the places under them and under the others. */
    fun add() {
        val current = _state.value
        val reading = current.reading as? EnrichReading.Read ?: return
        val stepRepo = trips ?: return
        val placeRepo = places ?: return
        if (current.saving || chosenCount(reading, current.ticked) == 0) return
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            var added = 0
            var failed = false
            reading.destinations.forEachIndexed { d, destination ->
                var anchor: Pair<Double, Double>? = null
                val stepId = if (destination.isNew) {
                    if (destinationKey(d) !in current.ticked) return@forEachIndexed
                    val id = stepRepo.newStepId(tripId)
                    val stay = withPosition(destination.toStay(id))
                    if (!write { stepRepo.saveStep(tripId, stay, isNew = true, remindAt = null) }) {
                        failed = true
                        return@forEachIndexed
                    }
                    added++
                    if (stay.lat != null && stay.lng != null) anchor = stay.lat to stay.lng
                    id
                } else {
                    val id = destination.existingStepId ?: return@forEachIndexed
                    anchor = anchors[id]
                    id
                }
                destination.places.forEachIndexed { p, place ->
                    if (placeKey(d, p) !in current.ticked) return@forEachIndexed
                    val point = placePosition(place, destination.city, anchor)
                    val written = TripPlace(
                        id = placeRepo.newPlaceId(tripId),
                        stepId = stepId,
                        kind = place.kind,
                        name = place.name,
                        address = place.address,
                        // Never a point given by the AI: the phone looks for it (règle 29), else by hand from its page.
                        lat = point?.first,
                        lng = point?.second,
                        arriveTime = place.arriveTime,
                        note = place.note,
                        cost = place.cost,
                    )
                    if (write { placeRepo.savePlace(tripId, written, isNew = true) }) added++ else failed = true
                }
            }
            _state.value = _state.value.copy(saving = false, saveFailed = failed, added = added)
        }
    }

    /** A new stay gets its point from the phone, as a stay typed by hand; none when offline or unknown. */
    private suspend fun withPosition(step: TripStep): TripStep {
        val finder = locator ?: return step
        val found = try {
            withTimeoutOrNull(GEOCODE_MILLIS) { finder.find(step.place) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return if (found == null) step else step.copy(lat = found.lat, lng = found.lng)
    }

    /**
     * The point of a place of the answer (récit 112, règle 29), found by the phone from its address, or
     * its name, and the city: kept only within [PLACE_RADIUS_METERS] of its destination, so a place of
     * the same name elsewhere is never put on the map. None without the point of the destination.
     */
    private suspend fun placePosition(place: ProposedPlace, city: String, anchor: Pair<Double, Double>?): Pair<Double, Double>? {
        val finder = locator ?: return null
        val near = anchor ?: return null
        val query = listOf(place.address ?: place.name, city).joinToString(", ")
        val found = try {
            withTimeoutOrNull(GEOCODE_MILLIS) { finder.find(query) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return (found.lat to found.lng).takeIf { nearEnough(it, near) }
    }

    /** True once the write is accepted, or still pending after the timeout (queued offline). */
    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        const val GEOCODE_MILLIS = 3_000L

        /** How far from its destination a place found by the phone may be. */
        const val PLACE_RADIUS_METERS = 50_000
    }
}

/** True when [point] is within [TripEnrichViewModel.PLACE_RADIUS_METERS] of [destination]. */
internal fun nearEnough(point: Pair<Double, Double>, destination: Pair<Double, Double>): Boolean =
    distanceMeters(point.first, point.second, destination.first, destination.second) <= TripEnrichViewModel.PLACE_RADIUS_METERS
