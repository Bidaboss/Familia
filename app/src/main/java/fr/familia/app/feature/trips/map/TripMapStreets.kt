package fr.familia.app.feature.trips.map

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import fr.familia.app.core.model.TripLandscape
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/*
 * « La carte avec les rues » (PRODUIT-147): the ground of the map is one image of the Static Images
 * API of Mapbox, asked for the area of the trip, instead of the SDK. Nothing is installed in the
 * app, nothing is sent but the area and the public token, and the points are placed by the same
 * Web Mercator maths as the image, so they sit on their streets. When the image does not come
 * (no network, no token, a refusal), the landscape stays under the points.
 */

/** The size of the image asked for, in points of the map: ratio [MAP_ASPECT], twice that with @2x. */
const val STREETS_WIDTH = 576
const val STREETS_HEIGHT = 640

/** At 512 points a tile, the world is 512 × 2^zoom points wide (Web Mercator, as the Static Images API). */
private const val WORLD_AT_ZOOM_0 = 512.0

/** The closest zoom (a street) and the farthest (the whole world is never useful for a trip). */
private const val MAX_ZOOM = 15.0
private const val MIN_ZOOM = 1.0

/** The area of the image: its centre and zoom, as the API takes them. */
data class StreetsFrame(val lat: Double, val lng: Double, val zoom: Double)

private fun mercatorX(lng: Double) = (lng + 180.0) / 360.0

private fun mercatorY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    return 0.5 - ln(tan(PI / 4 + Math.toRadians(clamped) / 2)) / (2 * PI)
}

private fun latitudeOf(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

/**
 * The area that shows every one of [points] with a [margin] around, centred, on an image
 * [width] × [height]. The zoom is cut to two decimals downwards (the API rounds to two), so a point
 * is never pushed to the edge, and kept between [MIN_ZOOM] and [MAX_ZOOM]: two steps in the same
 * street do not zoom into a wall.
 */
fun streetsFrame(points: List<GeoPoint>, width: Int = STREETS_WIDTH, height: Int = STREETS_HEIGHT, margin: Float = MAP_MARGIN): StreetsFrame? {
    if (points.isEmpty()) return null
    val xs = points.map { mercatorX(it.lng) }
    val ys = points.map { mercatorY(it.lat) }
    val minSpan = MIN_SPAN_DEGREES / 360.0
    val spanX = max(xs.max() - xs.min(), minSpan)
    val spanY = max(ys.max() - ys.min(), minSpan)
    val room = 1.0 - 2 * margin
    val scale = min(width * room / spanX, height * room / spanY)
    val zoom = (ln(scale / WORLD_AT_ZOOM_0) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val cutZoom = floor(zoom * 100) / 100
    val centerX = (xs.max() + xs.min()) / 2
    val centerY = (ys.max() + ys.min()) / 2
    return StreetsFrame(latitudeOf(centerY), centerX * 360.0 - 180.0, cutZoom)
}

/** Where each of [points] falls on the image of [frame], each coordinate between 0 and 1. */
fun projectStreets(points: List<GeoPoint>, frame: StreetsFrame, width: Int = STREETS_WIDTH, height: Int = STREETS_HEIGHT): List<UnitPoint> {
    val world = WORLD_AT_ZOOM_0 * 2.0.pow(frame.zoom)
    val cx = mercatorX(frame.lng)
    val cy = mercatorY(frame.lat)
    return points.map { p ->
        val px = width / 2.0 + (mercatorX(p.lng) - cx) * world
        val py = height / 2.0 + (mercatorY(p.lat) - cy) * world
        UnitPoint((px / width).toFloat().coerceIn(0f, 1f), (py / height).toFloat().coerceIn(0f, 1f))
    }
}

/** The request of the image, @2x for the screens of today. The token is public and only in the APK of the family. */
fun streetsUrl(frame: StreetsFrame, token: String): String {
    val centre = String.format(Locale.ROOT, "%.5f,%.5f,%.2f", frame.lng, frame.lat, frame.zoom)
    return "https://api.mapbox.com/styles/v1/mapbox/streets-v12/static/$centre/${STREETS_WIDTH}x$STREETS_HEIGHT@2x?access_token=$token"
}

/**
 * The image of the streets as the ground of the map, with the landscape under it while it comes and
 * when it does not (the logo and the attribution of Mapbox are in the image, as the terms ask).
 */
@Composable
fun BoxScope.StreetsBackground(url: String, landscape: TripLandscape) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
        loading = { LandscapeBackground(landscape) },
        error = { LandscapeBackground(landscape) },
    )
}
