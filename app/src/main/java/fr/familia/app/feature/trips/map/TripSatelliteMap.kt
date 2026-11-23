package fr.familia.app.feature.trips.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import kotlinx.coroutines.delay

/** What the satellite home of a trip needs to know about the map (récit 100). */
internal fun satelliteMapUsable(token: String, usable: Boolean, talkBack: Boolean): Boolean = token.isNotBlank() && usable && !talkBack

/**
 * The satellite map of the home of a trip (récit 100): the steps as points joined by a dotted white
 * line, framed above the bottom sheet ([bottomPaddingDp] is the part the sheet hides). It only
 * draws; when the picture is not there in 6 s (slow phone, no network) or the view cannot start,
 * [onFailed] tells the screen, which falls back on the drawn landscape.
 */
@Composable
fun TripSatelliteMap(
    token: String,
    points: List<GeoPoint>,
    /** The place of each point, written under it; the next step and the chosen one only would be quieter, later. */
    names: List<String>,
    nextIndex: Int,
    /** The sheet is high: the camera comes closer to the next step; low: all the steps above the sheet. */
    sheetHigh: Boolean,
    bottomPaddingDp: Double,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var drawn by remember { mutableStateOf(false) }
    // The listener of the map is cancelled with the screen.
    val lastHigh = remember { booleanArrayOf(false) }
    val listener = remember { arrayOfNulls<com.mapbox.common.Cancelable>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { listener[0]?.cancel() } }
    LaunchedEffect(Unit) {
        delay(6_000L)
        if (!drawn) onFailed()
    }
    AndroidView(
        modifier = modifier,
        factory = {
            runCatching {
                MapboxOptions.accessToken = token
                MapView(context).also { view ->
                    val density = context.resources.displayMetrics.density
                    view.mapboxMap.setCamera(frame(view, points, bottomPaddingDp * density))
                    listener[0] = view.mapboxMap.subscribeMapLoaded { drawn = true }
                    view.mapboxMap.loadStyle(Style.STANDARD_SATELLITE) { style ->
                        runCatching {
                            configureSatelliteGlobe(style)
                            drawSteps(style, points, names, nextIndex)
                        }.onFailure { onFailed() }
                    }
                }
            }.getOrElse {
                onFailed()
                android.view.View(context)
            }
        },
        update = { view ->
            val map = (view as? MapView) ?: return@AndroidView
            if (!drawn || lastHigh[0] == sheetHigh) return@AndroidView
            lastHigh[0] = sheetHigh
            val density = context.resources.displayMetrics.density
            val target = if (sheetHigh && points.getOrNull(nextIndex) != null) {
                CameraOptions.Builder().center(points[nextIndex].toPoint()).zoom(7.0).build()
            } else {
                frame(map, points, bottomPaddingDp * density)
            }
            map.camera.easeTo(target, com.mapbox.maps.plugin.animation.MapAnimationOptions.mapAnimationOptions { duration(600L) })
        },
    )
}

private fun frame(view: MapView, points: List<GeoPoint>, bottomPx: Double): CameraOptions {
    if (points.size == 1) return CameraOptions.Builder().center(points.first().toPoint()).zoom(9.0).build()
    val padding = EdgeInsets(120.0, 60.0, bottomPx + 60.0, 60.0)
    return view.mapboxMap.cameraForCoordinates(points.map { it.toPoint() }, CameraOptions.Builder().build(), padding, null, null)
}

private fun drawSteps(style: Style, points: List<GeoPoint>, names: List<String>, nextIndex: Int) {
    if (points.size >= 2) {
        style.addSource(geoJsonSource("home-trail") { geometry(LineString.fromLngLats(points.map { it.toPoint() })) })
        style.addLayer(lineLayer("home-trail-line", "home-trail") { lineColor("#FFFFFF"); lineWidth(3.0); lineDasharray(listOf(2.0, 2.0)) })
    }
    // One feature per step, with its number and its place for the texts.
    fun collection(indices: List<Int>) = FeatureCollection.fromFeatures(
        indices.map { i ->
            Feature.fromGeometry(points[i].toPoint()).also {
                it.addStringProperty("n", "${i + 1}")
                it.addStringProperty("name", names.getOrElse(i) { "" })
            }
        },
    )
    val others = points.indices.filter { it != nextIndex }
    val next = points.indices.filter { it == nextIndex }
    style.addSource(geoJsonSource("home-steps") { featureCollection(collection(others)) })
    style.addSource(geoJsonSource("home-next") { featureCollection(collection(next)) })
    style.addLayer(circleLayer("home-steps-dots", "home-steps") { circleRadius(13.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    style.addLayer(circleLayer("home-next-dot", "home-next") { circleRadius(18.0); circleColor("#FFD84A"); circleStrokeColor("#111111"); circleStrokeWidth(4.0) })
    // The number in the round, the place under it (the font of the style, white halo for the contrast).
    style.addLayer(symbolLayer("home-steps-numbers", "home-steps") { textField(get("n")); textSize(13.0); textColor("#FFFFFF"); textAllowOverlap(true) })
    style.addLayer(symbolLayer("home-next-number", "home-next") { textField(get("n")); textSize(15.0); textColor("#111111"); textAllowOverlap(true) })
    style.addLayer(
        symbolLayer("home-names", "home-steps") {
            textField(get("name")); textSize(12.0); textColor("#111111"); textHaloColor("#FFFFFF"); textHaloWidth(2.0); textOffset(listOf(0.0, 1.8))
        },
    )
    style.addLayer(
        symbolLayer("home-next-name", "home-next") {
            textField(get("name")); textSize(14.0); textColor("#111111"); textHaloColor("#FFD84A"); textHaloWidth(2.5); textOffset(listOf(0.0, 2.2))
        },
    )
}
