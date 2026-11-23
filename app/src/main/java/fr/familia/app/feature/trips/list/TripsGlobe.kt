package fr.familia.app.feature.trips.list

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.configureSatelliteGlobe
import fr.familia.app.feature.trips.map.toPoint
import kotlinx.coroutines.delay

/** One point of the globe of the list: where a trip goes. [highlighted]: the next trip, in black. */
data class GlobeMarker(val point: GeoPoint, val highlighted: Boolean)

/** The flight to a trip the member opened: the camera goes there in [FLIGHT_MILLIS], then the trip opens. */
data class GlobeFlight(val id: Int, val tripId: String, val point: GeoPoint)

internal const val FLIGHT_MILLIS = 2_000L

/** Degrees per second: one turn in 90 s (récit 99). */
internal const val GLOBE_DEGREES_PER_SECOND = 4.0

/** The longitude after [elapsedNanos] of rotation from the west to the east, kept in -180..180. */
internal fun turnedLongitude(lng: Double, elapsedNanos: Long): Double {
    val next = lng + GLOBE_DEGREES_PER_SECOND * elapsedNanos / 1_000_000_000.0
    return ((next + 540.0) % 360.0) - 180.0
}

/** Whether the globe turns: only while it is seen, not asked to stop, and the phone does not ask to save energy. */
internal fun globeTurns(started: Boolean, stoppedByMember: Boolean, powerSave: Boolean, animationsOff: Boolean): Boolean =
    started && !stoppedByMember && !powerSave && !animationsOff

internal fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

internal fun talkBackOn(context: Context): Boolean =
    (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

private fun powerSaveOn(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

/**
 * The satellite globe of the list « Voyages » (récit 99): turns slowly from the west to the east,
 * one point per trip. It stops when the screen is not seen, in energy saving, with « réduire les
 * animations » (then it stays still) or when [stopped]. Created with the public [token]; when the
 * view or its style (3 s) cannot start, [onFailed] tells the screen, which falls back on the drawn
 * landscape.
 */
@Composable
fun TripsGlobe(
    token: String,
    markers: List<GlobeMarker>,
    souvenirs: Boolean,
    stopped: Boolean,
    flight: GlobeFlight?,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val started = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    var powerSave by remember { mutableStateOf(powerSaveOn(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { powerSave = powerSaveOn(context) }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val stillAnyway = remember { animationsOff(context) }
    val turns = globeTurns(started, stopped || flight != null, powerSave, stillAnyway)
    val first = markers.firstOrNull { it.highlighted } ?: markers.firstOrNull()
    var view by remember { mutableStateOf<MapView?>(null) }
    var ready by remember { mutableStateOf(false) }
    // 6 s to draw the globe (style and imagery): a phone too slow or a network too poor gives it up for the landscape.
    LaunchedEffect(Unit) {
        delay(6_000L)
        if (!ready) onFailed()
    }
    // A trip opened: the camera flies there (the globe turns, tilts, comes closer), then the screen opens the trip.
    LaunchedEffect(flight?.id, view) {
        val target = flight ?: return@LaunchedEffect
        val map = view ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        map.camera.flyTo(
            CameraOptions.Builder().center(target.point.toPoint()).zoom(4.5).pitch(50.0).build(),
            mapAnimationOptions { duration(FLIGHT_MILLIS) },
        )
    }
    // The rotation: one camera move per frame, at most 30 per second, 15 when the phone struggles.
    LaunchedEffect(view, turns, ready) {
        val map = view?.mapboxMap ?: return@LaunchedEffect
        if (!turns || !ready) return@LaunchedEffect
        var lng = map.cameraState.center.longitude()
        var last = 0L
        var gap = 33_000_000L
        var slow = 0
        var frames = 0
        var since = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    since = now
                    return@withFrameNanos
                }
                if (now - last < gap) return@withFrameNanos
                val began = System.nanoTime()
                lng = turnedLongitude(lng, now - last)
                map.setCamera(CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(lng, 0.0)).build())
                // Measure: when moving the camera costs more than a frame, the rate is halved.
                if (System.nanoTime() - began > 25_000_000L) slow++ else slow = 0
                if (slow >= 10 && gap < 66_000_000L) {
                    gap = 66_000_000L
                    Log.d("TripsGlobe", "frame rate lowered to 15 fps")
                }
                frames++
                if (now - since > 10_000_000_000L) {
                    Log.d("TripsGlobe", "frames: ${frames / 10.0}/s, gap ${gap / 1_000_000} ms")
                    frames = 0
                    since = now
                }
                last = now
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            runCatching {
                MapboxOptions.accessToken = token
                MapView(context).also { map ->
                    map.mapboxMap.setCamera(
                        CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(first?.point?.lng ?: 0.0, 0.0)).zoom(0.6).pitch(20.0).build(),
                    )
                    map.mapboxMap.loadStyle(Style.STANDARD_SATELLITE) { style ->
                        runCatching {
                            configureSatelliteGlobe(style)
                            addMarkers(style, markers, souvenirs)
                        }.onFailure { onFailed() }
                    }
                    // Shown only once the picture is really drawn: a black void is worse than the landscape.
                    map.mapboxMap.subscribeMapLoaded { ready = true }
                    view = map
                }
            }.getOrElse {
                onFailed()
                android.view.View(context)
            }
        },
    )
}

private fun addMarkers(style: Style, markers: List<GlobeMarker>, souvenirs: Boolean) {
    fun collection(list: List<GlobeMarker>) = FeatureCollection.fromFeatures(list.map { Feature.fromGeometry(it.point.toPoint()) })
    style.addSource(geoJsonSource("trips-other") { featureCollection(collection(markers.filter { !it.highlighted })) })
    style.addSource(geoJsonSource("trips-next") { featureCollection(collection(markers.filter { it.highlighted })) })
    style.addLayer(
        circleLayer("trips-other-dots", "trips-other") {
            circleRadius(7.0)
            circleColor(if (souvenirs) "#FFFFFF" else "#FFD84A")
            circleStrokeColor(if (souvenirs) "#E8833A" else "#FFFFFF")
            circleStrokeWidth(2.0)
        },
    )
    style.addLayer(
        circleLayer("trips-next-dots", "trips-next") {
            circleRadius(9.0)
            circleColor("#111111")
            circleStrokeColor("#FFFFFF")
            circleStrokeWidth(3.0)
        },
    )
}
