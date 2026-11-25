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
                    // E-49: the classic Satellite, with the imagery in its own colours, as the globe of the list.
                    view.mapboxMap.loadStyle(GlobeLook.LIST_DEFAULT.styleUri) { style ->
                        runCatching {
                            configureSatelliteGlobe(style, GlobeLook.LIST_DEFAULT)
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

/** E-66: the config keys of Standard (Satellite) that write on the globe or draw lines on it. */
internal val GLOBE_HIDDEN_CONFIG = listOf(
    "showPlaceLabels", "showRoadLabels", "showPointOfInterestLabels", "showTransitLabels",
    "showAdminBoundaries", "showRoadsAndTransit", "showPedestrianRoads",
)

/** E-66: the layers a classic style may have that are not imagery: names, borders, roads. */
internal fun isWritingOrLine(type: String): Boolean = type == "symbol" || type == "line"

/**
 * E-66: the globe of « Voyages » is a clean Earth, nothing written, no border, no road. Called
 * before the app adds its own layers (the dots of the trips), so only the style's are removed.
 * The names in French stay on the map of a trip, which does not call this.
 */
internal fun cleanGlobe(style: Style) {
    GLOBE_HIDDEN_CONFIG.forEach { key -> runCatching { style.setStyleImportConfigProperty("basemap", key, Value.valueOf(false)) } }
    runCatching {
        style.styleLayers.filter { isWritingOrLine(it.type) }.forEach { layer -> runCatching { style.removeStyleLayer(layer.id) } }
    }
}

/** The beige of the lands and the blue of the seas of the drawn Earth under the satellite images (retour du 24/11, minuit). */
internal const val DRAWN_LAND = "#D8C9A3"
internal const val DRAWN_WATER = "#8FB8DE"
internal const val DRAWN_WATER_LAYER = "globe-drawn-water"

/** Where the drawn Earth goes in a satellite style: its background, its first raster layer, its vector source. */
internal data class DrawnEarthPlan(val backgroundId: String?, val rasterId: String, val vectorSourceId: String?, val bottomId: String)

/**
 * Pure rule: [layers] and [sources] as (id, type), bottom first. Null when the style has no image
 * layer (the drawn Earth of Standard needs nothing under it).
 */
internal fun drawnEarthPlan(layers: List<Pair<String, String>>, sources: List<Pair<String, String>>): DrawnEarthPlan? {
    val raster = layers.firstOrNull { it.second == "raster" }?.first ?: return null
    return DrawnEarthPlan(
        backgroundId = layers.firstOrNull { it.second == "background" }?.first,
        rasterId = raster,
        vectorSourceId = sources.firstOrNull { it.second == "vector" }?.first,
        bottomId = layers.first().first,
    )
}

/**
 * Retour du 24/11, minuit: the satellite images take a few seconds to come and the globe was black
 * meanwhile. The style now paints its lands beige and its seas blue (from its own vector tiles,
 * which come first) under the images, which cover them as they arrive.
 */
internal fun underlayDrawnEarth(style: Style) {
    runCatching {
        val plan = drawnEarthPlan(
            style.styleLayers.map { it.id to it.type },
            style.styleSources.map { it.id to it.type },
        ) ?: return
        if (plan.backgroundId != null) {
            style.setStyleLayerProperty(plan.backgroundId, "background-color", Value.valueOf(DRAWN_LAND))
        } else {
            style.addStyleLayer(
                Value.valueOf(hashMapOf("id" to Value.valueOf("globe-drawn-land"), "type" to Value.valueOf("background"), "paint" to Value.valueOf(hashMapOf("background-color" to Value.valueOf(DRAWN_LAND))))),
                com.mapbox.maps.LayerPosition(null, plan.bottomId, null),
            )
        }
        val vector = plan.vectorSourceId ?: return
        if (style.styleLayerExists(DRAWN_WATER_LAYER)) return
        style.addStyleLayer(
            Value.valueOf(
                hashMapOf(
                    "id" to Value.valueOf(DRAWN_WATER_LAYER),
                    "type" to Value.valueOf("fill"),
                    "source" to Value.valueOf(vector),
                    "source-layer" to Value.valueOf("water"),
                    "paint" to Value.valueOf(hashMapOf("fill-color" to Value.valueOf(DRAWN_WATER))),
                ),
            ),
            com.mapbox.maps.LayerPosition(null, plan.rasterId, null),
        )
    }.onFailure { android.util.Log.w("TripsGlobe", "drawn Earth under the images refused", it) }
}

/** The elevation of Mapbox (free with the public token), and the shading drawn from it on the globe. */
internal const val GLOBE_DEM_SOURCE = "globe-dem"
internal const val GLOBE_DEM_URL = "mapbox://mapbox.mapbox-terrain-dem-v1"
internal const val GLOBE_HILLSHADE_LAYER = "globe-hillshade"

/** The elevation source of the globe, added once. */
private fun addGlobeDem(style: Style) {
    if (style.styleSourceExists(GLOBE_DEM_SOURCE)) return
    style.addStyleSource(
        GLOBE_DEM_SOURCE,
        Value.valueOf(hashMapOf("type" to Value.valueOf("raster-dem"), "url" to Value.valueOf(GLOBE_DEM_URL), "tileSize" to Value.valueOf(512L))),
    )
}

/** How high the mountains stand when the fingers zoom in (1: true height). */
internal const val GLOBE_TERRAIN_EXAGGERATION = 1.3

/**
 * Retour du 25/11, 7 h 05 (« zoomer pour parcourir le monde »): the true relief in 3D, which only
 * shows once zoomed in on a region; seen whole, the globe does not change.
 */
internal fun addGlobeTerrain(style: Style) {
    runCatching {
        addGlobeDem(style)
        style.setStyleTerrain(
            Value.valueOf(hashMapOf("source" to Value.valueOf(GLOBE_DEM_SOURCE), "exaggeration" to Value.valueOf(GLOBE_TERRAIN_EXAGGERATION))),
        )
    }.onFailure { android.util.Log.w("TripsGlobe", "terrain refused", it) }
}

/** Pure rule: the paint of the images for [pop] (0 leaves them as they come). */
internal fun imageryPop(pop: Double): Map<String, Double> =
    if (pop <= 0.0) {
        emptyMap()
    } else {
        // Forests greener, deserts warmer, oceans deeper: more colour and contrast, the lightest parts a little darker.
        mapOf(
            "raster-saturation" to pop.coerceAtMost(0.6),
            "raster-contrast" to (pop * 0.6).coerceAtMost(0.4),
            "raster-brightness-max" to (1.0 - pop * 0.3).coerceAtLeast(0.8),
        )
    }

/**
 * Retour du 25/11 matin: the mountains shaded over the satellite images, at every zoom of the globe
 * (the elevation exists from zoom 0), and the images a little stronger. Nothing for a look without relief.
 */
internal fun addGlobeRelief(style: Style, look: GlobeLook) {
    if (look.relief <= 0.0 && look.pop <= 0.0) return
    runCatching {
        val raster = style.styleLayers.firstOrNull { it.type == "raster" }?.id ?: return
        imageryPop(look.pop).forEach { (key, value) -> runCatching { style.setStyleLayerProperty(raster, key, Value.valueOf(value)) } }
        if (look.relief <= 0.0 || style.styleLayerExists(GLOBE_HILLSHADE_LAYER)) return
        addGlobeDem(style)
        style.addStyleLayer(
            Value.valueOf(
                hashMapOf(
                    "id" to Value.valueOf(GLOBE_HILLSHADE_LAYER),
                    "type" to Value.valueOf("hillshade"),
                    "source" to Value.valueOf(GLOBE_DEM_SOURCE),
                    "paint" to Value.valueOf(
                        hashMapOf(
                            "hillshade-exaggeration" to Value.valueOf(look.relief.coerceIn(0.0, 1.0)),
                            "hillshade-shadow-color" to Value.valueOf("#2B1D10"),
                            "hillshade-highlight-color" to Value.valueOf("#FFF6E5"),
                            "hillshade-accent-color" to Value.valueOf("#3A2A18"),
                            "hillshade-illumination-anchor" to Value.valueOf("map"),
                        ),
                    ),
                ),
            ),
            com.mapbox.maps.LayerPosition(raster, null, null),
        )
    }.onFailure { android.util.Log.w("TripsGlobe", "relief refused", it) }
}
