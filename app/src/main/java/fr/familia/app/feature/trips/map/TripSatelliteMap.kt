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
import com.mapbox.maps.extension.style.layers.generated.lineLayer
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
    nextIndex: Int,
    bottomPaddingDp: Double,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var drawn by remember { mutableStateOf(false) }
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
                    view.mapboxMap.subscribeMapLoaded { drawn = true }
                    view.mapboxMap.loadStyle(Style.STANDARD_SATELLITE) { style ->
                        runCatching {
                            configureSatelliteGlobe(style)
                            drawSteps(style, points, nextIndex)
                        }.onFailure { onFailed() }
                    }
                }
            }.getOrElse {
                onFailed()
                android.view.View(context)
            }
        },
    )
}

private fun frame(view: MapView, points: List<GeoPoint>, bottomPx: Double): CameraOptions {
    if (points.size == 1) return CameraOptions.Builder().center(points.first().toPoint()).zoom(9.0).build()
    val padding = EdgeInsets(120.0, 60.0, bottomPx + 60.0, 60.0)
    return view.mapboxMap.cameraForCoordinates(points.map { it.toPoint() }, CameraOptions.Builder().build(), padding, null, null)
}

private fun drawSteps(style: Style, points: List<GeoPoint>, nextIndex: Int) {
    if (points.size >= 2) {
        style.addSource(geoJsonSource("home-trail") { geometry(LineString.fromLngLats(points.map { it.toPoint() })) })
        style.addLayer(lineLayer("home-trail-line", "home-trail") { lineColor("#FFFFFF"); lineWidth(3.0); lineDasharray(listOf(2.0, 2.0)) })
    }
    fun collection(list: List<GeoPoint>) = FeatureCollection.fromFeatures(list.map { Feature.fromGeometry(it.toPoint()) })
    style.addSource(geoJsonSource("home-steps") { featureCollection(collection(points.filterIndexed { i, _ -> i != nextIndex })) })
    style.addSource(geoJsonSource("home-next") { featureCollection(collection(points.filterIndexed { i, _ -> i == nextIndex })) })
    style.addLayer(circleLayer("home-steps-dots", "home-steps") { circleRadius(8.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    style.addLayer(circleLayer("home-next-dot", "home-next") { circleRadius(12.0); circleColor("#FFD84A"); circleStrokeColor("#111111"); circleStrokeWidth(4.0) })
}
