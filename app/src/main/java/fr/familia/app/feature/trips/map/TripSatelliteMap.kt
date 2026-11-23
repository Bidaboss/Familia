package fr.familia.app.feature.trips.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.QueriedRenderedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.scalebar.scalebar
import fr.familia.app.R
import kotlinx.coroutines.delay

/** What the satellite home of a trip needs to know about the map (récit 100). */
internal fun satelliteMapUsable(token: String, usable: Boolean, talkBack: Boolean): Boolean = token.isNotBlank() && usable && !talkBack

/** The step to number: the departure (the first) has no number, the next ones are 1 to n (DESIGN-77 A). */
internal fun stepNumberLabel(index: Int, arrived: Boolean): String = when {
    arrived -> "✓"
    index == 0 -> ""
    else -> index.toString()
}

/** The two looks of the map settings (DESIGN-74/155): satellite or streets. */
enum class MapLook { Satellite, Streets }

/**
 * The satellite map of the home of a trip (récits 100, 101): the steps as round markers (the
 * departure without number, then 1 to n, a tick once arrived) joined by a dotted line, framed
 * above the bottom sheet ([bottomPaddingDp] is the part the sheet hides). Free to move and zoom
 * with the fingers: no + / − button, no locate button (règle 5); one discreet button for the
 * settings (satellite or streets, 2D or 3D). A touch on a marker chooses the step ([onSelect]),
 * which the camera comes closer to; [selected] null frames all the steps again. When the picture
 * is not there in 6 s (slow phone, no network) or the view cannot start, [onFailed] tells the screen,
 * which falls back on the drawn landscape.
 */
@Composable
fun TripSatelliteMap(
    token: String,
    points: List<GeoPoint>,
    /** The place of each point, written under the next step and the chosen one only. */
    names: List<String>,
    arrived: List<Boolean>,
    nextIndex: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    /** The sheet is high: the camera comes closer to the next step; low: all the steps above the sheet. */
    sheetHigh: Boolean,
    bottomPaddingDp: Double,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    /** The arrival of règle 4: out to Europe, then back in on the place; false for who asked for no animation. */
    intro: Boolean = true,
) {
    val context = LocalContext.current
    var drawn by remember { mutableStateOf(false) }
    var look by rememberSaveable { mutableStateOf(MapLook.Satellite) }
    var tilted by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    // A touch of the map stops the arrival at once (règle 3: a click interrupts the animation).
    var touched by remember { mutableStateOf(false) }
    val listener = remember { arrayOfNulls<com.mapbox.common.Cancelable>(1) }
    val view = remember { arrayOfNulls<MapView>(1) }
    val applied = remember { arrayOf<Any?>(null, null, null, null) } // look, tilted, sheetHigh, selected
    val density = context.resources.displayMetrics.density
    DisposableEffect(Unit) { onDispose { listener[0]?.cancel() } }
    LaunchedEffect(Unit) {
        delay(6_000L)
        if (!drawn) onFailed()
    }
    // The arrival: out to Europe, then in on the trip, unless the person touches the map.
    LaunchedEffect(drawn) {
        if (!drawn || !intro) return@LaunchedEffect
        val map = view[0] ?: return@LaunchedEffect
        if (touched) return@LaunchedEffect
        map.camera.easeTo(CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(8.0, 47.0)).zoom(3.2).build(), mapAnimationOptions { duration(1_300L) })
        delay(1_400L)
        if (touched) return@LaunchedEffect
        map.camera.flyTo(frame(map, points, bottomPaddingDp * density, tilted), mapAnimationOptions { duration(2_400L) })
    }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touched = true
            }
        },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                runCatching {
                    MapboxOptions.accessToken = token
                    MapView(context).also { map ->
                        view[0] = map
                        // Règle 5: nothing but the map; the compass and the scale bar go too.
                        runCatching { map.compass.enabled = false; map.scalebar.enabled = false }
                        map.mapboxMap.setCamera(frame(map, points, bottomPaddingDp * density, tilted))
                        listener[0] = map.mapboxMap.subscribeMapLoaded { drawn = true }
                        loadLook(map, look, points, names, arrived, nextIndex, selected, onFailed)
                        applied[0] = look; applied[1] = tilted; applied[2] = sheetHigh; applied[3] = selected
                        map.gestures.addOnMapClickListener(
                            OnMapClickListener { point ->
                                map.mapboxMap.queryRenderedFeatures(
                                    RenderedQueryGeometry(map.mapboxMap.pixelForCoordinate(point)),
                                    RenderedQueryOptions(listOf("home-steps-dots", "home-done-dots", "home-next-dot", "home-cluster"), null),
                                ) { result ->
                                    val feature = result.value?.firstOrNull()?.queriedFeature?.feature
                                    val index = feature?.getNumberProperty("i")?.toInt()
                                    when {
                                        feature?.hasProperty("point_count") == true -> map.camera.easeTo(
                                            CameraOptions.Builder().center(point).zoom(map.mapboxMap.cameraState.zoom + 2.0).build(),
                                            mapAnimationOptions { duration(500L) },
                                        )
                                        index != null -> onSelect(index)
                                    }
                                }
                                false
                            },
                        )
                    }
                }.getOrElse {
                    onFailed()
                    android.view.View(context)
                }
            },
            update = { v ->
                val map = (v as? MapView) ?: return@AndroidView
                if (!drawn) return@AndroidView
                val padding = bottomPaddingDp * density
                if (applied[0] != look) {
                    applied[0] = look
                    loadLook(map, look, points, names, arrived, nextIndex, selected, onFailed)
                }
                if (applied[1] != tilted || applied[2] != sheetHigh || applied[3] != selected) {
                    val target = when {
                        selected != null && points.getOrNull(selected) != null ->
                            CameraOptions.Builder().center(points[selected].toPoint()).zoom(10.0).pitch(if (tilted) 60.0 else 0.0).build()
                        sheetHigh && points.getOrNull(nextIndex) != null ->
                            CameraOptions.Builder().center(points[nextIndex].toPoint()).zoom(7.0).pitch(if (tilted) 60.0 else 0.0).build()
                        else -> frame(map, points, padding, tilted)
                    }
                    if (applied[3] != selected) {
                        map.mapboxMap.getStyle()?.let { style -> showSelected(style, points, names, selected) }
                    }
                    applied[1] = tilted; applied[2] = sheetHigh; applied[3] = selected
                    map.camera.easeTo(target, mapAnimationOptions { duration(if (selected != null) 900L else 600L) })
                }
            },
        )
        // The only control: discreet, at the bottom right of the map, above the sheet.
        FilledTonalIconButton(
            onClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (bottomPaddingDp + 12.0).dp).size(48.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.trip_map_settings))
        }
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text(stringResource(R.string.trip_map_settings)) },
            text = {
                Column {
                    Row { 
                        FilterChip(look == MapLook.Satellite, { look = MapLook.Satellite }, { Text(stringResource(R.string.trip_map_look_satellite)) }, Modifier.heightIn(min = 56.dp).padding(end = 8.dp))
                        FilterChip(look == MapLook.Streets, { look = MapLook.Streets }, { Text(stringResource(R.string.trip_map_look_streets)) }, Modifier.heightIn(min = 56.dp))
                    }
                    Row {
                        FilterChip(!tilted, { tilted = false }, { Text(stringResource(R.string.trip_map_mode_2d)) }, Modifier.heightIn(min = 56.dp).padding(end = 8.dp))
                        FilterChip(tilted, { tilted = true }, { Text(stringResource(R.string.trip_map_mode_3d)) }, Modifier.heightIn(min = 56.dp))
                    }
                }
            },
            confirmButton = { TextButton({ settingsOpen = false }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.trip_map_settings_done)) } },
        )
    }
}

private fun frame(view: MapView, points: List<GeoPoint>, bottomPx: Double, tilted: Boolean = false): CameraOptions {
    val pitch = if (tilted) 60.0 else 0.0
    if (points.size == 1) return CameraOptions.Builder().center(points.first().toPoint()).zoom(9.0).pitch(pitch).build()
    val padding = EdgeInsets(120.0, 60.0, bottomPx + 60.0, 60.0)
    return view.mapboxMap.cameraForCoordinates(points.map { it.toPoint() }, CameraOptions.Builder().pitch(pitch).build(), padding, null, null)
}

/** Loads the look of the map, then draws the steps on it (again after a change of look: the style forgets them). */
private fun loadLook(view: MapView, look: MapLook, points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, next: Int, selected: Int?, onFailed: () -> Unit) {
    val satellite = look == MapLook.Satellite
    view.mapboxMap.loadStyle(if (satellite) Style.STANDARD_SATELLITE else Style.STANDARD) { style ->
        runCatching {
            if (satellite) configureSatelliteGlobe(style)
            drawSteps(style, points, names, arrived, next, selected, trail = if (satellite) "#FFFFFF" else "#333333")
        }.onFailure { onFailed() }
    }
}

private fun feature(points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, i: Int) =
    Feature.fromGeometry(points[i].toPoint()).also {
        it.addNumberProperty("i", i)
        it.addStringProperty("n", stepNumberLabel(i, arrived.getOrElse(i) { false }))
        it.addStringProperty("name", names.getOrElse(i) { "" })
        it.addNumberProperty("done", if (arrived.getOrElse(i) { false }) 1 else 0)
    }

private fun showSelected(style: Style, points: List<GeoPoint>, names: List<String>, selected: Int?) {
    val features = if (selected != null && points.getOrNull(selected) != null) listOf(feature(points, names, emptyList(), selected)) else emptyList()
    style.getSourceAs<GeoJsonSource>("home-selected")?.featureCollection(FeatureCollection.fromFeatures(features))
}

private fun drawSteps(style: Style, points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, nextIndex: Int, selected: Int?, trail: String) {
    if (points.size >= 2) {
        style.addSource(geoJsonSource("home-trail") { geometry(LineString.fromLngLats(points.map { it.toPoint() })) })
        style.addLayer(lineLayer("home-trail-line", "home-trail") { lineColor(trail); lineWidth(3.0); lineDasharray(listOf(2.0, 2.0)) })
    }
    val others = points.indices.filter { it != nextIndex }
    // The close steps gather in one round with their number, and come apart when the map is zoomed (règle 7, PRODUIT 23/11).
    style.addSource(
        geoJsonSource("home-steps") {
            featureCollection(FeatureCollection.fromFeatures(others.map { feature(points, names, arrived, it) }))
            cluster(true)
            clusterRadius(28L)
            clusterMaxZoom(11L)
        },
    )
    style.addSource(geoJsonSource("home-next") { featureCollection(FeatureCollection.fromFeatures(points.indices.filter { it == nextIndex }.map { feature(points, names, arrived, it) })) })
    style.addSource(geoJsonSource("home-selected") { featureCollection(FeatureCollection.fromFeatures(emptyList())) })
    val single = Expression.not(Expression.has("point_count"))
    val notDone = Expression.eq(Expression.get("done"), Expression.literal(0.0))
    val done = Expression.eq(Expression.get("done"), Expression.literal(1.0))
    style.addLayer(circleLayer("home-cluster", "home-steps") { filter(Expression.has("point_count")); circleRadius(18.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    style.addLayer(symbolLayer("home-cluster-count", "home-steps") { filter(Expression.has("point_count")); textField(get("point_count_abbreviated")); textSize(13.0); textColor("#FFFFFF"); textAllowOverlap(true) })
    style.addLayer(circleLayer("home-steps-dots", "home-steps") { filter(Expression.all(single, notDone)); circleRadius(13.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    style.addLayer(circleLayer("home-done-dots", "home-steps") { filter(Expression.all(single, done)); circleRadius(13.0); circleColor("#2E7D32"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    style.addLayer(circleLayer("home-next-dot", "home-next") { circleRadius(18.0); circleColor("#FFD84A"); circleStrokeColor("#111111"); circleStrokeWidth(4.0) })
    style.addLayer(circleLayer("home-selected-ring", "home-selected") { circleRadius(24.0); circleColor("#00000000"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(4.0) })
    // The number in the round (none for the departure), a tick once arrived.
    style.addLayer(symbolLayer("home-steps-numbers", "home-steps") { filter(single); textField(get("n")); textSize(13.0); textColor("#FFFFFF"); textAllowOverlap(true) })
    style.addLayer(symbolLayer("home-next-number", "home-next") { textField(get("n")); textSize(15.0); textColor("#111111"); textAllowOverlap(true) })
    // The place is written under the next step and the chosen one only.
    style.addLayer(
        symbolLayer("home-next-name", "home-next") {
            textField(get("name")); textSize(14.0); textColor("#111111"); textHaloColor("#FFD84A"); textHaloWidth(2.5); textOffset(listOf(0.0, 2.2))
        },
    )
    style.addLayer(
        symbolLayer("home-selected-name", "home-selected") {
            textField(get("name")); textSize(14.0); textColor("#111111"); textHaloColor("#FFFFFF"); textHaloWidth(2.5); textOffset(listOf(0.0, 2.4))
        },
    )
    showSelected(style, points, names, selected)
}
