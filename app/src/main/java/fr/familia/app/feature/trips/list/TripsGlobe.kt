package fr.familia.app.feature.trips.list

import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.MapView
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.GlobeHealth
import fr.familia.app.feature.trips.map.MAP_STYLE_TIMEOUT_MS
import fr.familia.app.feature.trips.map.MapFailureLog
import fr.familia.app.feature.trips.map.MapUnavailable
import fr.familia.app.feature.trips.map.mapErrorLine
import fr.familia.app.feature.trips.map.viewErrorLine
import fr.familia.app.feature.trips.map.mapFailureReason
import fr.familia.app.feature.trips.map.configureSatelliteGlobe
import fr.familia.app.feature.trips.map.toPoint
import kotlinx.coroutines.delay

/** One point of the globe of the list: where a trip goes. [highlighted]: the next trip, a bigger dot. [name]: its label (E-38). */
data class GlobeMarker(val point: GeoPoint, val highlighted: Boolean, val tripId: String = "", val name: String = "")

/** One dot drawn on the globe: the trips that go to the same place (about 1 km) share it, with their number. */
data class GlobeSpot(val point: GeoPoint, val highlighted: Boolean, val tripIds: List<String>, val name: String = "")

/** Groups the trips going to the same place into one dot (spec liste-voyages-globe : « un seul point avec leur nombre »), in the order of [markers]. */
internal fun globeSpots(markers: List<GlobeMarker>): List<GlobeSpot> =
    markers.groupBy { Math.round(it.point.lat * 100) to Math.round(it.point.lng * 100) }.values.map { same ->
        GlobeSpot(same.first().point, same.any { it.highlighted }, same.map { it.tripId }, same.firstOrNull { it.name.isNotBlank() }?.name.orEmpty())
    }

/** The trip a touch on [spot] opens: the next one when it is there, else the first in the list. */
internal fun spotTrip(spot: GlobeSpot?): String? = spot?.tripIds?.firstOrNull()

/** The flight to a trip the member opened: the camera goes there in [FLIGHT_MILLIS], then the trip opens. */
data class GlobeFlight(val id: Int, val tripId: String, val point: GeoPoint)

internal const val FLIGHT_MILLIS = 2_000L

/** E-40: the zoom of the globe of the list, about 80 % of the width of a phone as in the prototype. */
internal const val GLOBE_LIST_ZOOM = 1.0

/** Degrees per second: one turn in 90 s (récit 99). */
internal const val GLOBE_DEGREES_PER_SECOND = 4.0

/** E-33: after a touch the globe stays where the finger left it, then turns again after this pause. */
internal const val GLOBE_RESUME_AFTER_TOUCH_MS = 3_000L

/**
 * E-34: the globe gives up for the landscape only when its style never came. Once the style is
 * there it is the globe that shows, even while the imagery still comes, and nothing replaces it.
 */
internal fun globeGivesUp(styleLoaded: Boolean): Boolean = !styleLoaded

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
 * view or its style (15 s) cannot start, [onFailed] tells the screen, which falls back on the drawn
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
    /** A touch on a dot: the trip there (the next one when several trips share the place). */
    onOpenTrip: (String) -> Unit = {},
    /** The destinations added by hand to the carnet (récit 107, règle 38): a simple mark in Souvenirs, no line, no touch. */
    handAdded: List<GeoPoint> = emptyList(),
    /** How big the globe is: [GLOBE_LIST_ZOOM] on the list, smaller in a short frame. */
    zoom: Double = GLOBE_LIST_ZOOM,
    /** The accent yellow of the app (the pill of the bottom bar): the dots of the coming trips (E-38). */
    accent: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFF2C14E),
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
    val spots = remember(markers) { globeSpots(markers) }
    val currentSpots = androidx.compose.runtime.rememberUpdatedState(spots)
    val currentOpen = androidx.compose.runtime.rememberUpdatedState(onOpenTrip)
    var view by remember { mutableStateOf<MapView?>(null) }
    // The style is loaded and its layers are there: the globe shows, turns and takes its dots (E-33, E-34).
    var ready by remember { mutableStateOf(false) }
    // Kept, or Mapbox drops the subscription once nothing holds it (E-33, E-34: ready never came).
    val listeners = remember { arrayOfNulls<com.mapbox.common.Cancelable>(2) }
    DisposableEffect(Unit) { onDispose { listeners.forEach { it?.cancel() } } }
    // The last error Mapbox gave, for « Plantages ».
    val lastError = remember { arrayOfNulls<String>(1) }
    val gaveUp = remember { booleanArrayOf(false) }
    val giveUp: (String?) -> Unit = { sdkError ->
        if (!gaveUp[0]) {
            gaveUp[0] = true
            MapFailureLog.record(context, mapFailureReason(token, MapFailureLog.online(context), sdkError ?: lastError[0], null, timedOut = sdkError == null))
            onFailed()
        }
    }
    // A finger on the globe: it stops while the finger moves it, then turns again a little later (E-33).
    var moving by remember { mutableStateOf(false) }
    var touches by remember { mutableStateOf(0) }
    // À venir and Souvenirs share the globe: switching only redraws its dots.
    LaunchedEffect(view, ready, spots, souvenirs) {
        val style = view?.mapboxMap?.style ?: return@LaunchedEffect
        if (ready) showSpots(style, spots, souvenirs, context)
    }
    LaunchedEffect(view, ready, handAdded, souvenirs) {
        val style = view?.mapboxMap?.style ?: return@LaunchedEffect
        if (ready) showHandAdded(style, if (souvenirs) handAdded else emptyList())
    }
    // 15 s to draw the globe (style and imagery): a phone too slow or a network too poor gives it up for the
    // landscape, and the reason goes to « Plantages » (retour d'essai du 24/11).
    LaunchedEffect(Unit) {
        delay(MAP_STYLE_TIMEOUT_MS)
        if (globeGivesUp(ready)) giveUp(null)
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
    LaunchedEffect(view, turns, ready, moving, touches) {
        val map = view?.mapboxMap ?: return@LaunchedEffect
        if (!turns || !ready || moving) return@LaunchedEffect
        if (touches > 0) delay(GLOBE_RESUME_AFTER_TOUCH_MS)
        // From where the finger left it: its longitude and its latitude.
        var lng = map.cameraState.center.longitude()
        val lat = map.cameraState.center.latitude()
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
                map.setCamera(CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(lng, lat)).build())
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
                    // Règle 5 (E-25): nothing but the globe, no scale over « Voyages », no compass.
                    runCatching { map.compass.enabled = false; map.scalebar.enabled = false }
                    map.mapboxMap.setCamera(
                        CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(first?.point?.lng ?: 0.0, 0.0)).zoom(zoom).pitch(20.0).build(),
                    )
                    listeners[0] = map.mapboxMap.subscribeMapLoadingError { error ->
                        lastError[0] = mapErrorLine(error.type.toString(), error.message)
                        Log.w("TripsGlobe", "map error ${lastError[0]}")
                        // A missing tile leaves the globe; only a style that cannot load is the end, and only before it showed.
                        if (error.type == MapLoadingErrorType.STYLE && !ready) giveUp(lastError[0])
                    }
                    map.mapboxMap.loadStyle(Style.STANDARD_SATELLITE) { style ->
                        runCatching {
                            configureSatelliteGlobe(style)
                            // Before the trips: its layer lies under their dots.
                            addHandAdded(style)
                            addMarkers(style, accentHex(accent))
                            showSpots(style, currentSpots.value, souvenirs, context)
                        }.onFailure {
                            // A layer refused: the globe stays (never the landscape over a map that shows), the reason goes to « Plantages ».
                            Log.w("TripsGlobe", "layer refused", it)
                            MapFailureLog.record(context, MapUnavailable.SdkError("couche ${viewErrorLine(it)}".take(60)))
                        }
                        ready = true
                        GlobeHealth.markWorking(context)
                    }
                    // A touch on a dot opens its trip (with the flight); anywhere else, nothing.
                    map.gestures.addOnMapClickListener(
                        OnMapClickListener { point ->
                            map.mapboxMap.queryRenderedFeatures(
                                // A 56 dp square around the finger: the dots are smaller than a finger.
                                RenderedQueryGeometry(touchBox(map.mapboxMap.pixelForCoordinate(point), 28.0 * map.resources.displayMetrics.density)),
                                RenderedQueryOptions(SPOT_LAYERS, null),
                            ) { result ->
                                val index = result.value?.firstOrNull()?.queriedFeature?.feature?.getNumberProperty("i")?.toInt()
                                spotTrip(index?.let { currentSpots.value.getOrNull(it) })?.let { currentOpen.value(it) }
                            }
                            false
                        },
                    )
                    // The finger moves the globe: no rotation under it; it starts again after a pause (E-33).
                    map.gestures.addOnMoveListener(
                        object : OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) { moving = true }
                            override fun onMove(detector: MoveGestureDetector): Boolean = false
                            override fun onMoveEnd(detector: MoveGestureDetector) { moving = false; touches++ }
                        },
                    )
                    // Everything drawn: also ready, in case the style came another way.
                    listeners[1] = map.mapboxMap.subscribeMapLoaded { ready = true; GlobeHealth.markWorking(context) }
                    view = map
                }
            }.getOrElse {
                onFailed()
                android.view.View(context)
            }
        },
    )
}

private fun touchBox(at: ScreenCoordinate, half: Double) =
    ScreenBox(ScreenCoordinate(at.x - half, at.y - half), ScreenCoordinate(at.x + half, at.y + half))

private val SPOT_LAYERS = listOf("trips-other-dots", "trips-next-dots", "trips-past-dots")

private fun spotFeature(spot: GlobeSpot, index: Int): Feature =
    Feature.fromGeometry(spot.point.toPoint()).also {
        it.addNumberProperty("i", index)
        it.addStringProperty("count", if (spot.tripIds.size > 1) spot.tripIds.size.toString() else "")
        if (spot.name.isNotBlank()) it.addStringProperty("label", labelImageId(spot.name))
    }

/** « #F2C14E » for a Mapbox layer. */
internal fun accentHex(color: androidx.compose.ui.graphics.Color): String =
    String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

/** The image id of the label of a place (E-38): one per name, kept in the style. */
internal fun labelImageId(name: String): String = "trip-label-" + Integer.toHexString(name.hashCode())

/**
 * The dots of the tab shown (E-38, prototype): the coming trips in the accent yellow, the next one
 * bigger; the past ones white with an orange ring. Under each dot, a white rounded label with the name.
 */
private fun showSpots(style: Style, spots: List<GlobeSpot>, souvenirs: Boolean, context: Context) {
    fun collection(keep: (GlobeSpot) -> Boolean) =
        FeatureCollection.fromFeatures(spots.mapIndexedNotNull { i, spot -> if (keep(spot)) spotFeature(spot, i) else null })
    spots.map { it.name }.filter { it.isNotBlank() }.distinct().forEach { name ->
        val id = labelImageId(name)
        if (!style.hasStyleImage(id)) style.addImage(id, labelBitmap(context, name))
    }
    style.getSourceAs<GeoJsonSource>("trips-other")?.featureCollection(collection { !souvenirs && !it.highlighted })
    style.getSourceAs<GeoJsonSource>("trips-next")?.featureCollection(collection { !souvenirs && it.highlighted })
    style.getSourceAs<GeoJsonSource>("trips-past")?.featureCollection(collection { souvenirs })
}

/** A white rounded label with the name of the place, dark bold text, as the prototype. */
private fun labelBitmap(context: Context, name: String): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    // C of #592: a long name ends with « … » rather than cut short.
    val text = if (name.length > 28) name.take(27).trimEnd() + "…" else name
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF17333F.toInt()
        textSize = 12f * context.resources.configuration.fontScale.coerceAtMost(1.3f) * density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val padX = 8f * density
    val padY = 4f * density
    val width = (paint.measureText(text) + 2 * padX).toInt().coerceAtLeast(1)
    val height = (paint.fontMetrics.let { it.descent - it.ascent } + 2 * padY).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val back = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, back)
    canvas.drawText(text, padX, padY - paint.fontMetrics.ascent, paint)
    return bitmap
}

private fun addMarkers(style: Style, accent: String) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    style.addSource(geoJsonSource("trips-other") { featureCollection(empty) })
    style.addSource(geoJsonSource("trips-next") { featureCollection(empty) })
    style.addSource(geoJsonSource("trips-past") { featureCollection(empty) })
    // The labels first: they lie under the dots.
    listOf("trips-other", "trips-next", "trips-past").forEach { source ->
        style.addLayer(
            symbolLayer("$source-labels", source) {
                iconImage(get("label"))
                iconAnchor(com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor.TOP)
                iconOffset(listOf(0.0, 16.0))
                iconAllowOverlap(false)
            },
        )
    }
    style.addLayer(circleLayer("trips-other-dots", "trips-other") { circleRadius(9.0); circleColor(accent); circleStrokeColor("#FFFFFF"); circleStrokeWidth(2.0) })
    style.addLayer(circleLayer("trips-past-dots", "trips-past") { circleRadius(9.0); circleColor("#FFFFFF"); circleStrokeColor("#E8833A"); circleStrokeWidth(2.0) })
    style.addLayer(circleLayer("trips-next-dots", "trips-next") { circleRadius(12.0); circleColor(accent); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    // Several trips at the same place: their number in the dot.
    style.addLayer(symbolLayer("trips-other-count", "trips-other") { textField(get("count")); textSize(12.0); textColor("#111111"); textAllowOverlap(true) })
    style.addLayer(symbolLayer("trips-past-count", "trips-past") { textField(get("count")); textSize(12.0); textColor("#111111"); textAllowOverlap(true) })
    style.addLayer(symbolLayer("trips-next-count", "trips-next") { textField(get("count")); textSize(13.0); textColor("#111111"); textAllowOverlap(true) })
}

/*
 * The destinations added by hand to « Carnet de baroudeur » (récit 107, règle 38): their own source
 * and layer, beside the trips (never in SPOT_LAYERS: a touch there opens nothing). A small white
 * dot ringed in coral, under the dots of the trips.
 */
private const val HAND_ADDED_SOURCE = "carnet-hand-added"

private fun addHandAdded(style: Style) {
    style.addSource(geoJsonSource(HAND_ADDED_SOURCE) { featureCollection(FeatureCollection.fromFeatures(emptyList())) })
    style.addLayer(circleLayer("carnet-hand-added-marks", HAND_ADDED_SOURCE) { circleRadius(6.0); circleColor("#FFFFFF"); circleStrokeColor("#D9604A"); circleStrokeWidth(2.5) })
}

private fun showHandAdded(style: Style, points: List<GeoPoint>) {
    style.getSourceAs<GeoJsonSource>(HAND_ADDED_SOURCE)?.featureCollection(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it.toPoint()) }))
}
