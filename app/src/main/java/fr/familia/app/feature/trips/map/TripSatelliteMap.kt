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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateMapOf
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
import android.graphics.Bitmap
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.MapView
import com.mapbox.maps.QueriedRenderedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.localization.localizeLabels
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
import fr.familia.app.core.photo.LocalPhotoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.log2

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
 * is not there in 15 s (slow phone, no network) or the view cannot start, [onFailed] tells the screen,
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
    /** The map gave up; its reason, short, goes under « Carte indisponible » (retour d'essai du 24/11). */
    onFailed: (MapUnavailable) -> Unit,
    modifier: Modifier = Modifier,
    /** Fiche F1 §3: the round « couches » button at the top right, under the bubble, instead of above the sheet. */
    settingsAtTop: Boolean = false,
    /** The arrival of règle 4: out to Europe, then back in on the place; false for who asked for no animation. */
    intro: Boolean = true,
    /** The text of each round (« Le trajet » numbers the destinations); null: the departure without number, then 1 to n. */
    labels: List<String>? = null,
    /** The thumbnail of the first photo of each point (récit 110): shown in its round instead of the plain dot. */
    photoPaths: List<String?> = emptyList(),
    /** « Modifier » (récit 111, règle 4): the arrival goes out to the whole globe, then back in on the steps. */
    globeIntro: Boolean = false,
    /** The time the sheet takes to go down before the camera moves (the reference video: the sheet first). */
    introDelayMs: Long = 0L,
) {
    val context = LocalContext.current
    var drawn by remember { mutableStateOf(false) }
    // The settings kept for the person (récit 110): Satellite or Rues, 2D or 3D.
    val saved = remember { MapSettingsStore.read(context) }
    var look by rememberSaveable { mutableStateOf(saved.look) }
    var tilted by rememberSaveable { mutableStateOf(saved.tilted) }
    var settingsOpen by remember { mutableStateOf(false) }
    // A touch of the map stops the arrival at once (règle 3: a click interrupts the animation).
    var touched by remember { mutableStateOf(false) }
    // Règle 25: back from a page opened here, the map is where it was, and the arrival does not play again.
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    val memory = rememberSaveable(saver = CameraMemory.Saver) { CameraMemory() }
    // Back from a page opened here (règle 25): the kept camera, never a new frame.
    val restored = remember { memory.camera != null }
    val listener = remember { arrayOfNulls<com.mapbox.common.Cancelable>(4) }
    // Why it failed, for « Plantages » (retour d'essai du 24/11): the last error Mapbox gave, once only.
    val lastError = remember { arrayOfNulls<String>(1) }
    val gaveUp = remember { booleanArrayOf(false) }
    // A change of look (Satellite or Rues) loads a new style: its failure leaves an empty map, so it counts too (C1 of #573).
    val restyling = remember { booleanArrayOf(false) }
    val fail: (Throwable?, Boolean) -> Unit = { viewError, timedOut ->
        if (!gaveUp[0]) {
            gaveUp[0] = true
            val reason = mapFailureReason(token, MapFailureLog.online(context), lastError[0], viewError, timedOut)
            MapFailureLog.record(context, reason)
            GlobeHealth.markFailed(context)
            onFailed(reason)
        }
    }
    val view = remember { arrayOfNulls<MapView>(1) }
    val applied = remember { arrayOf<Any?>(null, null, null, null, null, null) } // look, tilted, sheetHigh, selected, photos, sheet height
    val density = context.resources.displayMetrics.density
    // The rounds with a photo (récit 110), drawn once each photo is loaded; the others stay plain dots.
    val storage = LocalPhotoStorage.current
    val photoMarkers = remember { mutableStateMapOf<Int, Bitmap>() }
    LaunchedEffect(photoPaths, labels, nextIndex, arrived, storage) {
        photoMarkers.keys.retainAll { photoPaths.getOrNull(it) != null }
        val files = storage ?: return@LaunchedEffect
        // A few at a time (relecture #547, C2): each round shows as soon as its photo is there.
        val gate = Semaphore(MarkerPhotos.AT_ONCE)
        coroutineScope {
            photoPaths.forEachIndexed { i, path ->
                if (path == null) return@forEachIndexed
                launch {
                    gate.withPermit {
                        val url = try {
                            files.downloadUrl(path)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            files.knownUrl(path)
                        } ?: return@withPermit
                        val label = labels?.getOrNull(i) ?: stepNumberLabel(i, arrived.getOrElse(i) { false })
                        val next = i == nextIndex
                        MarkerPhotos.load(context, url, if (next) MarkerPhotos.NEXT_SIZE_DP else MarkerPhotos.SIZE_DP, label, next)?.let { photoMarkers[i] = it }
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { listener.forEach { it?.cancel() } } }
    LaunchedEffect(Unit) {
        delay(MAP_STYLE_TIMEOUT_MS)
        if (!drawn) fail(null, true)
    }
    // The arrival: out to the continent of the trip, then in on the trip, unless the person touches the map.
    LaunchedEffect(drawn) {
        if (!drawn) return@LaunchedEffect
        // C1 of #580: made before its layout, the view started on the middle of the steps; now it has
        // its size, the frame on all the steps is right. The arrival does it itself when it plays.
        if (!restored && !touched && (!intro || introPlayed)) {
            view[0]?.let { map -> runCatching { map.mapboxMap.setCamera(frame(map, points, bottomPaddingDp * density, tilted)) } }
        }
        if (!intro || introPlayed) return@LaunchedEffect
        val map = view[0] ?: return@LaunchedEffect
        if (touched) return@LaunchedEffect
        introPlayed = true
        val middle = com.mapbox.geojson.Point.fromLngLat(points.map { it.lng }.average(), points.map { it.lat }.average())
        if (globeIntro) {
            // Règle 4, as on the reference video: out to the whole Earth, then one dive back on all the steps.
            delay(introDelayMs)
            if (touched) return@LaunchedEffect
            map.camera.easeTo(CameraOptions.Builder().center(middle).zoom(EditArrival.GLOBE_ZOOM).pitch(0.0).bearing(0.0).build(), mapAnimationOptions { duration(EditArrival.OUT_MS) })
            delay(EditArrival.OUT_MS + 50L)
            if (touched) return@LaunchedEffect
            map.camera.flyTo(frame(map, points, bottomPaddingDp * density, tilted), mapAnimationOptions { duration(EditArrival.IN_MS) })
            return@LaunchedEffect
        }
        map.camera.easeTo(CameraOptions.Builder().center(middle).zoom(3.2).build(), mapAnimationOptions { duration(EditArrival.HOME_OUT_MS) })
        delay(EditArrival.HOME_OUT_MS + 100L)
        if (touched) return@LaunchedEffect
        map.camera.flyTo(frame(map, points, bottomPaddingDp * density, tilted), mapAnimationOptions { duration(EditArrival.HOME_IN_MS) })
    }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // DESIGN-101 B: the 6 s arrival stops where it is, not only before its next part.
                if (!touched && introPlayed) view[0]?.camera?.cancelAllAnimators()
                touched = true
                introPlayed = true
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
                        map.mapboxMap.setCamera(memory.camera?.let(::cameraOf) ?: frame(map, points, bottomPaddingDp * density, tilted))
                        keepAboveSheet(map, bottomPaddingDp, density)
                        applied[5] = bottomPaddingDp
                        // The style is there: the map shows, even while the last tiles still come (slow phone, slow network).
                        listener[0] = map.mapboxMap.subscribeMapLoaded { drawn = true; GlobeHealth.markWorking(context) }
                        listener[2] = map.mapboxMap.subscribeStyleLoaded { drawn = true; restyling[0] = false; GlobeHealth.markWorking(context) }
                        listener[3] = map.mapboxMap.subscribeMapLoadingError { error ->
                            lastError[0] = mapErrorLine(error.type.toString(), error.message)
                            recordTileError(context, "TripSatelliteMap", error.type.toString(), error.sourceId, error.message)
                            // A missing tile or icon leaves the map; only a style that cannot load is the end.
                            if (error.type == MapLoadingErrorType.STYLE && (!drawn || restyling[0])) fail(null, false)
                        }
                        listener[1] = map.mapboxMap.subscribeMapIdle {
                            val camera = map.mapboxMap.cameraState
                            memory.keep(camera.center.longitude(), camera.center.latitude(), camera.zoom, camera.bearing, camera.pitch)
                        }
                        loadLook(map, look, points, names, arrived, nextIndex, selected, { fail(it, false) }, labels, onRefused = { MapFailureLog.record(context, MapUnavailable.SdkError("couche $it".take(60))) }) { style -> showPhotos(style, points, names, arrived, nextIndex, labels, photoMarkers.toMap()) }
                        applied[0] = look; applied[1] = tilted; applied[2] = sheetHigh; applied[3] = selected; applied[4] = photoMarkers.toMap()
                        map.gestures.addOnMapClickListener(
                            OnMapClickListener { point ->
                                map.mapboxMap.queryRenderedFeatures(
                                    RenderedQueryGeometry(map.mapboxMap.pixelForCoordinate(point)),
                                    RenderedQueryOptions(listOf("home-steps-dots", "home-done-dots", "home-next-dot", "home-cluster", "home-photo-icons", "home-next-photo-icon"), null),
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
                }.getOrElse { error ->
                    fail(error, false)
                    android.view.View(context)
                }
            },
            update = { v ->
                val map = (v as? MapView) ?: return@AndroidView
                if (!drawn) return@AndroidView
                val padding = bottomPaddingDp * density
                if (applied[5] != bottomPaddingDp) {
                    applied[5] = bottomPaddingDp
                    keepAboveSheet(map, bottomPaddingDp, density)
                }
                val shownPhotos = photoMarkers.toMap()
                if (applied[0] != look) {
                    applied[0] = look
                    restyling[0] = true
                    applied[4] = shownPhotos
                    loadLook(map, look, points, names, arrived, nextIndex, selected, { fail(it, false) }, labels, onRefused = { MapFailureLog.record(context, MapUnavailable.SdkError("couche $it".take(60))) }) { style -> showPhotos(style, points, names, arrived, nextIndex, labels, shownPhotos) }
                } else if (applied[4] != shownPhotos) {
                    applied[4] = shownPhotos
                    map.mapboxMap.getStyle()?.let { style -> runCatching { showPhotos(style, points, names, arrived, nextIndex, labels, shownPhotos) } }
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
        if (settingsAtTop) {
            // Fiche F1 §3: a white round with the layers, at the top right under the bubble.
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                contentColor = androidx.compose.ui.graphics.Color(0xFF1E1B18),
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 116.dp, end = 12.dp),
            ) {
                androidx.compose.material3.IconButton(onClick = { settingsOpen = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.trip_map_settings))
                }
            }
        } else {
            // The only control: discreet, at the bottom right of the map, above the sheet.
            FilledTonalIconButton(
                onClick = { settingsOpen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (bottomPaddingDp + 12.0).dp).size(56.dp),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.trip_map_settings))
            }
        }
    }
    if (settingsOpen) {
        // The map follows each choice at once; « Enregistrer » keeps it for next time, « Annuler » puts back what was there.
        val before = remember { MapSettings(look, tilted) }
        val cancel = {
            look = before.look
            tilted = before.tilted
            settingsOpen = false
        }
        AlertDialog(
            onDismissRequest = cancel,
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
            confirmButton = {
                TextButton(
                    {
                        MapSettingsStore.save(context, MapSettings(look, tilted))
                        settingsOpen = false
                    },
                    Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.trip_map_settings_save)) }
            },
            dismissButton = { TextButton(cancel, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The camera kept by [CameraMemory]: longitude, latitude, zoom, bearing, pitch. */
private fun cameraOf(kept: DoubleArray): CameraOptions =
    CameraOptions.Builder().center(com.mapbox.geojson.Point.fromLngLat(kept[0], kept[1])).zoom(kept[2]).bearing(kept[3]).pitch(kept[4]).build()

private fun frame(view: MapView, points: List<GeoPoint>, bottomPx: Double, tilted: Boolean = false): CameraOptions {
    val pitch = if (tilted) 60.0 else 0.0
    val middle = CameraOptions.Builder()
        .center(com.mapbox.geojson.Point.fromLngLat(points.map { it.lng }.average(), points.map { it.lat }.average()))
        .zoom(if (points.size == 1) 9.0 else 6.0).pitch(pitch).build()
    if (points.size <= 1) return middle
    // Before its first layout the view has no size: margins larger than the map make no frame.
    // E-51: margins in dp (they were in pixels, about 22 dp on a phone): the last round and its name never touch the edge.
    val d = view.resources.displayMetrics.density.toDouble()
    val padding = EdgeInsets(FRAME_TOP_DP * d, FRAME_SIDE_DP * d, bottomPx + FRAME_SIDE_DP * d, FRAME_SIDE_DP * d)
    if (view.width <= 0 || view.height <= padding.top + padding.bottom || view.width <= padding.left + padding.right) return middle
    return runCatching {
        view.mapboxMap.cameraForCoordinates(points.map { it.toPoint() }, CameraOptions.Builder().pitch(pitch).build(), padding, null, null)
            .toBuilder().padding(EdgeInsets(0.0, 0.0, bottomPx, 0.0)).build()
    }.getOrDefault(middle)
}

/**
 * E-53: the middle of the map is the middle of the part above the sheet (the camera keeps the
 * sheet as its bottom padding), and zoomed out the whole globe stays above it, 16 dp from the edges.
 */
private fun keepAboveSheet(view: MapView, sheetDp: Double, density: Float) {
    runCatching {
        view.mapboxMap.setCamera(CameraOptions.Builder().padding(EdgeInsets(0.0, 0.0, sheetDp * density, 0.0)).build())
        val widthDp = view.width / density.toDouble()
        val heightDp = view.height / density.toDouble()
        if (widthDp > 0 && heightDp > 0) {
            view.mapboxMap.setBounds(CameraBoundsOptions.Builder().minZoom(wholeGlobeZoom(widthDp, heightDp - sheetDp)).build())
        }
    }
}

/**
 * E-53: the smallest zoom at which the whole globe fits in [widthDp] × [heightDp] with 16 dp on
 * each side. At zoom z the world is 512 × 2^z dp around, so the globe is 512 × 2^z / π across.
 */
internal fun wholeGlobeZoom(widthDp: Double, heightDp: Double): Double {
    val room = (minOf(widthDp, heightDp) - 2 * GLOBE_MARGIN_DP).coerceAtLeast(64.0)
    return log2(room * PI / 512.0).coerceIn(0.0, 3.0)
}

internal const val GLOBE_MARGIN_DP = 16.0

/** E-51: the room kept around the steps when the map frames them: a round of 45 dp with its name fits. */
internal const val FRAME_SIDE_DP = 56.0
internal const val FRAME_TOP_DP = 96.0

/** E-49: the style of the Satellite look of the map of a trip. */
internal val TRIP_SATELLITE_STYLE: String = Style.SATELLITE_STREETS

/** Loads the look of the map, then draws the steps on it (again after a change of look: the style forgets them). */
/** The layers without which there is no map of the trip: the rounds of the steps and their sources. */
internal val ESSENTIAL_LAYERS = listOf("home-steps", "home-next", "home-steps-dots", "home-done-dots", "home-next-dot")

private fun loadLook(view: MapView, look: MapLook, points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, next: Int, selected: Int?, onFailed: (Throwable) -> Unit, labels: List<String>?, onRefused: (String) -> Unit = {}, afterDraw: (Style) -> Unit = {}) {
    val satellite = look == MapLook.Satellite
    // E-49: Standard Satellite showed only its neutral vector background on the phones of the family;
    // the classic Satellite Streets has the imagery in its own colours, with the roads and the names.
    view.mapboxMap.loadStyle(if (satellite) TRIP_SATELLITE_STYLE else Style.STANDARD) { style ->
        runCatching {
            if (satellite) configureSatelliteGlobe(style, GlobeLook.LIST_DEFAULT)
            // E-54: the names of the map in French (continents, countries, towns), as the app.
            runCatching { style.localizeLabels(java.util.Locale.FRENCH) }
            val refused = drawSteps(style, points, names, arrived, next, selected, trail = if (satellite) "#FFFFFF" else "#333333", labels = labels)
            // Only the rounds are the map of the trip: a refused name, number or ring leaves it (and says so).
            if (refused.isNotEmpty()) onRefused(refused.first())
            if (refused.any { line -> ESSENTIAL_LAYERS.any { line.startsWith("$it:") } }) throw IllegalStateException(refused.joinToString(" ; "))
        }.onFailure { onFailed(it) }
        // The photos are an extra: a failure there leaves the plain rounds.
        runCatching { afterDraw(style) }
    }
}

/**
 * Puts the photo rounds (récit 110) over the plain dots. The photo is a property of the same points
 * (« img » on home-steps and home-next), so a photo round gathers into the same bubble as its plain
 * round and comes apart with it (relecture #547, C1). Called again when a photo arrives.
 */
private fun showPhotos(style: Style, points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, nextIndex: Int, labels: List<String>?, photos: Map<Int, Bitmap>) {
    photos.forEach { (i, bitmap) -> style.addImage("step-photo-$i", bitmap) }
    fun collection(indices: List<Int>) = FeatureCollection.fromFeatures(
        indices.map { i -> feature(points, names, arrived, i, labels).also { if (i in photos.keys) it.addStringProperty("img", "step-photo-$i") } },
    )
    style.getSourceAs<GeoJsonSource>("home-steps")?.featureCollection(collection(points.indices.filter { it != nextIndex }))
    style.getSourceAs<GeoJsonSource>("home-next")?.featureCollection(collection(points.indices.filter { it == nextIndex }))
    if (style.styleLayerExists("home-photo-icons")) return
    val withPhoto = Expression.has("img")
    style.addLayer(
        symbolLayer("home-photo-icons", "home-steps") {
            filter(Expression.all(Expression.not(Expression.has("point_count")), withPhoto))
            iconImage(get("img")); iconAllowOverlap(true); iconIgnorePlacement(true)
        },
    )
    style.addLayer(symbolLayer("home-next-photo-icon", "home-next") { filter(withPhoto); iconImage(get("img")); iconAllowOverlap(true); iconIgnorePlacement(true) })
}

private fun feature(points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, i: Int, labels: List<String>? = null) =
    Feature.fromGeometry(points[i].toPoint()).also {
        it.addNumberProperty("i", i)
        it.addStringProperty("n", labels?.getOrNull(i) ?: stepNumberLabel(i, arrived.getOrElse(i) { false }))
        it.addStringProperty("name", names.getOrElse(i) { "" })
        it.addNumberProperty("done", if (arrived.getOrElse(i) { false }) 1 else 0)
    }

private fun showSelected(style: Style, points: List<GeoPoint>, names: List<String>, selected: Int?) {
    val features = if (selected != null && points.getOrNull(selected) != null) listOf(feature(points, names, emptyList(), selected)) else emptyList()
    style.getSourceAs<GeoJsonSource>("home-selected")?.featureCollection(FeatureCollection.fromFeatures(features))
}

/**
 * Draws the steps; returns the layers Mapbox refused, « id: message » (retour d'essai du 24/11:
 * one refused layer used to cut the whole map). Each source and layer goes on its own, again
 * after a first try on the same style (a look changed, photos come).
 */
internal fun drawSteps(style: Style, points: List<GeoPoint>, names: List<String>, arrived: List<Boolean>, nextIndex: Int, selected: Int?, trail: String, labels: List<String>?): List<String> {
    val refused = mutableListOf<String>()
    fun addSource(source: com.mapbox.maps.extension.style.sources.Source) {
        runCatching { if (!style.styleSourceExists(source.sourceId)) style.addSource(source) }
            .onFailure { refused += "${source.sourceId}: ${it.message}" }
    }
    fun addLayer(layer: com.mapbox.maps.extension.style.layers.Layer) {
        runCatching {
            if (style.styleLayerExists(layer.layerId)) style.removeStyleLayer(layer.layerId)
            style.addLayer(layer)
        }.onFailure { refused += "${layer.layerId}: ${it.message}" }
    }
    if (points.size >= 2) {
        addSource(geoJsonSource("home-trail") { geometry(LineString.fromLngLats(points.map { it.toPoint() })) })
        addLayer(lineLayer("home-trail-line", "home-trail") { lineColor(trail); lineWidth(3.0); lineDasharray(listOf(2.0, 2.0)) })
    }
    val others = points.indices.filter { it != nextIndex }
    // The close steps gather in one round with their number, and come apart when the map is zoomed (règle 7, PRODUIT 23/11).
    addSource(
        geoJsonSource("home-steps") {
            featureCollection(FeatureCollection.fromFeatures(others.map { feature(points, names, arrived, it, labels) }))
            cluster(true)
            clusterRadius(28L)
            clusterMaxZoom(11L)
        },
    )
    addSource(geoJsonSource("home-next") { featureCollection(FeatureCollection.fromFeatures(points.indices.filter { it == nextIndex }.map { feature(points, names, arrived, it, labels) })) })
    addSource(geoJsonSource("home-selected") { featureCollection(FeatureCollection.fromFeatures(emptyList())) })
    val single = Expression.not(Expression.has("point_count"))
    val notDone = Expression.eq(Expression.get("done"), Expression.literal(0.0))
    val done = Expression.eq(Expression.get("done"), Expression.literal(1.0))
    addLayer(circleLayer("home-cluster", "home-steps") { filter(Expression.has("point_count")); circleRadius(18.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    addLayer(symbolLayer("home-cluster-count", "home-steps") { filter(Expression.has("point_count")); textField(get("point_count_abbreviated")); textSize(13.0); textColor("#FFFFFF"); textAllowOverlap(true) })
    addLayer(circleLayer("home-steps-dots", "home-steps") { filter(Expression.all(single, notDone)); circleRadius(13.0); circleColor("#111111"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    addLayer(circleLayer("home-done-dots", "home-steps") { filter(Expression.all(single, done)); circleRadius(13.0); circleColor("#2E7D32"); circleStrokeColor("#FFFFFF"); circleStrokeWidth(3.0) })
    addLayer(circleLayer("home-next-dot", "home-next") { circleRadius(18.0); circleColor("#FFD84A"); circleStrokeColor("#111111"); circleStrokeWidth(4.0) })
    addLayer(circleLayer("home-selected-ring", "home-selected") { circleRadius(24.0); circleColor("#000000"); circleOpacity(0.0); circleStrokeColor("#FFFFFF"); circleStrokeWidth(4.0) })
    // The number in the round (none for the departure), a tick once arrived.
    addLayer(symbolLayer("home-steps-numbers", "home-steps") { filter(single); textField(get("n")); textSize(13.0); textColor("#FFFFFF"); textAllowOverlap(true) })
    addLayer(symbolLayer("home-next-number", "home-next") { textField(get("n")); textSize(15.0); textColor("#111111"); textAllowOverlap(true) })
    // The place is written under the next step and the chosen one only.
    addLayer(
        symbolLayer("home-next-name", "home-next") {
            textField(get("name")); textSize(14.0); textColor("#111111"); textHaloColor("#FFD84A"); textHaloWidth(2.5); textOffset(listOf(0.0, 2.2))
        },
    )
    addLayer(
        symbolLayer("home-selected-name", "home-selected") {
            textField(get("name")); textSize(14.0); textColor("#111111"); textHaloColor("#FFFFFF"); textHaloWidth(2.5); textOffset(listOf(0.0, 2.4))
        },
    )
    runCatching { showSelected(style, points, names, selected) }
    return refused
}
