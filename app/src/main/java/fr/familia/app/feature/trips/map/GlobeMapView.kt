package fr.familia.app.feature.trips.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.camera

private const val TRAIL_ALL = "trail-all"
private const val TRAIL_DONE = "trail-done"
private const val STEPS = "steps"

/** What the globe is asked to do: the camera to reach, how long it takes, how many legs are drawn. */
data class GlobeCommand(val id: Int, val target: CameraTarget, val millis: Long, val completedLegs: Int)

/**
 * The map of Mapbox as a satellite globe (récit 99: the Satellite style, projection forced to globe), with
 * the trail of the trip and a point per step (récit 94). It only draws: [command] says where the
 * camera goes, [onArrived] says it got there. Created with the public [token] of this APK; when the
 * view or its style cannot start, [onFailed] tells the screen, which falls back on the flat map.
 */
@Composable
fun GlobeMapView(
    token: String,
    points: List<GeoPoint>,
    command: GlobeCommand,
    onArrived: (commandId: Int) -> Unit,
    onStyleReady: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lastCommand = remember { intArrayOf(Int.MIN_VALUE) }
    AndroidView(
        modifier = modifier,
        factory = {
            runCatching {
                MapboxOptions.accessToken = token
                MapView(context).also { view ->
                    view.mapboxMap.setCamera(command.target.toOptions())
                    view.mapboxMap.loadStyle(Style.STANDARD_SATELLITE) { style ->
                        runCatching {
                            configureSatelliteGlobe(style)
                            style.addSource(geoJsonSource(TRAIL_ALL) { geometry(trail(points, points.size - 1)) })
                            style.addSource(geoJsonSource(TRAIL_DONE) { geometry(trail(points, 0)) })
                            style.addSource(geoJsonSource(STEPS) { featureCollection(com.mapbox.geojson.FeatureCollection.fromFeatures(points.map { com.mapbox.geojson.Feature.fromGeometry(it.toPoint()) })) })
                            style.addLayer(lineLayer("trail-all-line", TRAIL_ALL) { lineColor("#FFE27A"); lineWidth(4.0) })
                            style.addLayer(lineLayer("trail-done-line", TRAIL_DONE) { lineColor("#FFFFFF"); lineWidth(5.0) })
                            style.addLayer(circleLayer("steps-dots", STEPS) { circleRadius(7.0); circleColor("#2E4A57"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(2.0) })
                        }.onFailure { onFailed() }
                        onStyleReady()
                    }
                }
            }.getOrElse {
                onFailed()
                android.view.View(context)
            }
        },
        update = { view ->
            val map = view as? MapView ?: return@AndroidView
            // Only a new command moves the camera: a recomposition for another reason must not fly again.
            if (lastCommand[0] == command.id) return@AndroidView
            lastCommand[0] = command.id
            // The trail drawn so far follows the engine.
            map.mapboxMap.getStyle()?.getSourceAs<GeoJsonSource>(TRAIL_DONE)?.geometry(trail(points, command.completedLegs))
            if (command.millis <= 0L) {
                map.mapboxMap.setCamera(command.target.toOptions())
            } else {
                var cancelled = false
                map.camera.flyTo(
                    command.target.toOptions(),
                    mapAnimationOptions { duration(command.millis) },
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) { cancelled = true }
                        override fun onAnimationEnd(animation: Animator) { if (!cancelled) onArrived(command.id) }
                    },
                )
            }
        },
    )
}

internal fun GeoPoint.toPoint(): Point = Point.fromLngLat(lng, lat)

internal fun CameraTarget.toOptions(): CameraOptions =
    CameraOptions.Builder().center(point.toPoint()).zoom(zoom).pitch(pitch).bearing(bearing).build()

/** The trail from the first step to step [upTo] (at least two points so the line exists). */
private fun trail(points: List<GeoPoint>, upTo: Int): LineString {
    val list = points.take((upTo + 1).coerceAtLeast(1)).map { it.toPoint() }
    return LineString.fromLngLats(if (list.size >= 2) list else listOf(list.first(), list.first()))
}

/** Satellite view, round, in a starry sky, with the sky of [look]. Shared by every globe of the app. */
internal fun configureSatelliteGlobe(style: Style, look: GlobeLook = GlobeLook.Standard) {
    // The globe projection; the rest only tunes the sky, and a refusal there must not give the globe up.
    runCatching { style.setStyleProjectionProperty("name", Value.valueOf("globe")) }
    val sky = hashMapOf(
        "color" to Value.valueOf(look.color),
        "high-color" to Value.valueOf(look.highColor),
        "space-color" to Value.valueOf(look.spaceColor),
        "horizon-blend" to Value.valueOf(look.horizonBlend),
        "star-intensity" to Value.valueOf(look.starIntensity),
    )
    // A classic style (Satellite) has no sky of its own: it is given whole; Standard Satellite has one, tuned key by key.
    val whole = runCatching { style.setStyleAtmosphere(Value.valueOf(sky)) }.getOrNull()?.isValue == true
    if (!whole) runCatching { sky.forEach { (key, value) -> style.setStyleAtmosphereProperty(key, value) } }
}
