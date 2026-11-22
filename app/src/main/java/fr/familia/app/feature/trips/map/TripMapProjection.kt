package fr.familia.app.feature.trips.map

import fr.familia.app.core.model.TripStep
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A point on the earth, in degrees. */
data class GeoPoint(val lat: Double, val lng: Double)

/** A point of the map, each coordinate between 0 (left, top) and 1 (right, bottom). */
data class UnitPoint(val x: Float, val y: Float)

/** The ratio width / height of the map of a trip (récit 87). */
const val MAP_ASPECT = 0.9f

/** The room left around the points, as a share of the map, so a point is never cut at the edge. */
const val MAP_MARGIN = 0.16f

/**
 * Where to draw each of [points] on a map of ratio [aspect] (width / height), north up. Pure, so it
 * is tested without a screen, and the same for any background: the landscape today, streets later.
 *
 * It is a flat projection (longitudes are shortened by the cosine of the middle latitude), fitted
 * so that every point shows with a [margin] around, and centered. The span is never smaller than
 * [MIN_SPAN_DEGREES], so two steps in the same town stay close instead of filling the map, and a
 * single step sits in the middle.
 */
fun projectToUnit(points: List<GeoPoint>, aspect: Float = MAP_ASPECT, margin: Float = MAP_MARGIN): List<UnitPoint> {
    if (points.isEmpty()) return emptyList()
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLng = points.minOf { it.lng }
    val maxLng = points.maxOf { it.lng }
    val midLat = (minLat + maxLat) / 2
    val midLng = (minLng + maxLng) / 2
    val flatten = cos(Math.toRadians(midLat)).coerceAtLeast(MIN_COSINE)
    val spanX = max((maxLng - minLng) * flatten, MIN_SPAN_DEGREES)
    val spanY = max(maxLat - minLat, MIN_SPAN_DEGREES)
    val room = 1f - 2 * margin
    // The map is 1 high and [aspect] wide; the largest scale that keeps both spans inside it.
    val scale = min(aspect * room / spanX, room / spanY)
    return points.map { p ->
        val wx = (p.lng - midLng) * flatten
        val wy = midLat - p.lat
        UnitPoint(
            x = (0.5 + wx * scale / aspect).toFloat().coerceIn(0f, 1f),
            y = (0.5 + wy * scale).toFloat().coerceIn(0f, 1f),
        )
    }
}

/** About 20 km: below it the points are not spread any wider. */
const val MIN_SPAN_DEGREES = 0.2

/** Near the poles the cosine goes to 0; this keeps the span from collapsing. */
private const val MIN_COSINE = 0.1

/** One step of the trip on the map: its rank among all the steps, and its point if it has one. */
data class MapStop(val step: TripStep, val number: Int, val total: Int, val point: UnitPoint?)

/**
 * The stops of [steps], already in the order of the trip. A step with no position keeps its rank
 * but has no [MapStop.point]: it stays in the list, never on the map.
 */
fun mapStops(steps: List<TripStep>, aspect: Float = MAP_ASPECT): List<MapStop> {
    val placed = steps.filter { it.hasPosition }
    val projected = projectToUnit(placed.map { GeoPoint(it.lat!!, it.lng!!) }, aspect)
    val pointOf = placed.map { it.id }.zip(projected).toMap()
    return steps.mapIndexed { i, step -> MapStop(step, i + 1, steps.size, pointOf[step.id]) }
}
