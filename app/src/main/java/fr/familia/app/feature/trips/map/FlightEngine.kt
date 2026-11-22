package fr.familia.app.feature.trips.map

import fr.familia.app.feature.trips.detail.flightKm
import kotlin.math.max
import kotlin.math.min

/*
 * Le moteur du survol (PRODUIT-148, récit 94, temps 2): what the camera does from step to step,
 * with no map, no screen and no clock of its own. The map (the SDK, later) only draws what this
 * says and tells it when a movement or a pause is over, so every rule is tested on a computer.
 */

/** Where the camera is looking: a place, how close, and how it is tilted and turned. */
data class CameraTarget(val point: GeoPoint, val zoom: Double, val pitch: Double = 0.0, val bearing: Double = 0.0)

/** What the survol is doing. [Overview]: the globe before leaving. [Flying]: going to step [to]. [Dwelling]: stopped on step [at]. [Done]: the end, the flat map. */
sealed interface FlightPhase {
    data object Overview : FlightPhase
    data class Flying(val to: Int) : FlightPhase
    data class Dwelling(val at: Int) : FlightPhase
    data object Done : FlightPhase
}

/** The state of the survol: the phase, and whether it is playing (false: paused, the host stops its timers). */
data class FlightState(val phase: FlightPhase = FlightPhase.Overview, val playing: Boolean = false)

/**
 * The survol of [points], the placed steps in the order of the trip. One step or none: nothing to
 * fly, [canFly] is false and the screen keeps the flat map.
 */
class FlightEngine(val points: List<GeoPoint>) {
    var state: FlightState = FlightState()
        private set

    val canFly: Boolean get() = points.size >= 2
    private val last get() = points.lastIndex

    /** The step the survol is on, or -1 before leaving, or the last one at the end. */
    val currentIndex: Int
        get() = when (val p = state.phase) {
            FlightPhase.Overview -> -1
            is FlightPhase.Flying -> p.to
            is FlightPhase.Dwelling -> p.at
            FlightPhase.Done -> last
        }

    val finished: Boolean get() = state.phase == FlightPhase.Done

    /** « Lancer le voyage »: leaves the globe for the first step. */
    fun start() {
        if (!canFly) return
        state = FlightState(FlightPhase.Flying(0), playing = true)
    }

    fun pause() {
        if (state.phase is FlightPhase.Flying || state.phase is FlightPhase.Dwelling) state = state.copy(playing = false)
    }

    fun resume() {
        if (state.phase is FlightPhase.Flying || state.phase is FlightPhase.Dwelling) state = state.copy(playing = true)
    }

    /** The host says the camera has arrived: the survol stops a moment on the step. */
    fun onMotionEnded() {
        val p = state.phase as? FlightPhase.Flying ?: return
        state = state.copy(phase = FlightPhase.Dwelling(p.to))
    }

    /** The host says the pause on a step is over: next step, or the end after the last. */
    fun onDwellEnded() {
        val p = state.phase as? FlightPhase.Dwelling ?: return
        state = state.copy(phase = if (p.at < last) FlightPhase.Flying(p.at + 1) else FlightPhase.Done)
    }

    /** « Suivant »: flies to the next step now; on the last, ends. */
    fun next() {
        if (state.phase == FlightPhase.Overview || state.phase == FlightPhase.Done) return
        val to = currentIndex + 1
        state = state.copy(phase = if (to <= last) FlightPhase.Flying(to) else FlightPhase.Done)
    }

    /** « Précédent »: back to the step before; on the first, stays there. */
    fun previous() {
        if (state.phase == FlightPhase.Overview || state.phase == FlightPhase.Done) return
        state = state.copy(phase = FlightPhase.Flying(max(0, currentIndex - 1)))
    }

    /** « Voir la carte »: straight to the end. */
    fun skip() {
        if (state.phase != FlightPhase.Overview) state = state.copy(phase = FlightPhase.Done, playing = false)
    }

    /** « Rejouer le survol »: back to the globe, ready to start. */
    fun reset() {
        state = FlightState()
    }

    /** Where the camera goes for the current phase. */
    fun camera(): CameraTarget? {
        if (points.isEmpty()) return null
        return when (val p = state.phase) {
            FlightPhase.Overview -> CameraTarget(points.first(), GLOBE_ZOOM)
            is FlightPhase.Flying -> stepCamera(p.to)
            is FlightPhase.Dwelling -> stepCamera(p.at)
            FlightPhase.Done -> endCamera()
        }
    }

    /** How long the camera takes to reach the step now being flown to, in milliseconds. */
    fun flyMillis(): Long {
        val to = (state.phase as? FlightPhase.Flying)?.to ?: return 0
        val from = if (to == 0) null else points[to - 1]
        return flightMillis(from, points[to])
    }

    /**
     * The legs of the trail already drawn in full: leg k joins step k to step k + 1. While flying to
     * step n the leg n - 1 is being drawn (the host animates it); at the end, all of them.
     */
    fun completedLegs(): Int = when (val p = state.phase) {
        FlightPhase.Overview -> 0
        is FlightPhase.Flying -> max(0, p.to - 1)
        is FlightPhase.Dwelling -> p.at
        FlightPhase.Done -> max(0, points.size - 1)
    }

    /** The leg being drawn while flying, or null. */
    fun drawingLeg(): Int? = (state.phase as? FlightPhase.Flying)?.to?.takeIf { it >= 1 }?.minus(1)

    private fun stepCamera(index: Int): CameraTarget {
        val near = nearestNeighbourKm(index)
        // Close to the next or the previous step: nearer; far: a wider view of the town.
        val zoom = when {
            near < 30 -> ZOOM_STREET
            near < 300 -> ZOOM_TOWN
            else -> ZOOM_REGION
        }
        return CameraTarget(points[index], zoom, pitch = if (zoom >= ZOOM_TOWN) PITCH else 0.0)
    }

    private fun endCamera(): CameraTarget {
        val frame = streetsFrame(points)
        return if (frame != null) CameraTarget(GeoPoint(frame.lat, frame.lng), frame.zoom) else CameraTarget(points.first(), ZOOM_REGION)
    }

    private fun nearestNeighbourKm(index: Int): Double {
        val here = points[index]
        val neighbours = listOfNotNull(points.getOrNull(index - 1), points.getOrNull(index + 1))
        return neighbours.minOfOrNull { flightKm(here.lat, here.lng, it.lat, it.lng) } ?: Double.MAX_VALUE
    }

    companion object {
        const val GLOBE_ZOOM = 1.5
        const val ZOOM_STREET = 12.0
        const val ZOOM_TOWN = 9.5
        const val ZOOM_REGION = 6.0
        const val PITCH = 40.0

        /** How long the pause on each step lasts, so five steps make about 40 seconds. */
        const val DWELL_MILLIS = 3_000L

        const val MIN_FLY_MILLIS = 2_500L
        const val MAX_FLY_MILLIS = 6_000L

        /** A short hop is quick, a crossing of the world slower, always between the two limits. */
        fun flightMillis(from: GeoPoint?, to: GeoPoint): Long {
            if (from == null) return 4_500L
            val km = flightKm(from.lat, from.lng, to.lat, to.lng)
            return min(MAX_FLY_MILLIS, max(MIN_FLY_MILLIS, (MIN_FLY_MILLIS + km * 2.5).toLong()))
        }
    }
}
