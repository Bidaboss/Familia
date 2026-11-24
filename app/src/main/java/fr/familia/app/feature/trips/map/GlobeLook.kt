package fr.familia.app.feature.trips.map

import android.content.Context
import com.mapbox.maps.Style

/**
 * How a globe looks (retour E-48): the imagery and the sky around it. The globe of « Voyages »
 * takes the variant kept on this phone; the other globes of the app keep [Standard]. A long touch
 * on the globe of the list goes to the next variant, so the admin compares them on a real phone;
 * the one kept becomes the default once chosen.
 */
enum class GlobeLook(
    /** The letter the admin sees: « Globe : variante A ». */
    val letter: String,
    val styleUri: String,
    /** The halo near the ground, the halo high up, the sky. */
    val color: String,
    val highColor: String,
    val spaceColor: String,
    val horizonBlend: Double,
    val starIntensity: Double,
    /** How big the globe of the list is: 1.1 fills about 85 % of the width of a phone. */
    val listZoom: Double,
) {
    /**
     * Natural colours from zoom 0 (blue oceans, green forests, ochre deserts), a vivid blue halo.
     * Retour du 24/11 soir: the imagery of Satellite Streets, the one the map of a trip shows on the
     * phone of the admin (classic Satellite stayed black there); its names and roads are taken off.
     */
    Vivid("A", Style.SATELLITE_STREETS, "#7FB8FF", "#1E6FE0", "#02050F", 0.10, 0.9, 1.1),

    /** The same imagery, a softer halo, a slightly bigger globe. */
    Soft("B", Style.SATELLITE_STREETS, "#A8CCF5", "#2F6FC8", "#030712", 0.06, 0.8, 1.2),

    /** Standard Satellite as before E-48, for the comparison and for the other globes. */
    Standard("C", Style.STANDARD_SATELLITE, "#BFD9F2", "#3C6FB5", "#050B1A", 0.04, 0.7, 1.0),
    ;

    /** The variant after this one, back to the first after the last. */
    fun next(): GlobeLook = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The variant of the list before any choice. */
        val LIST_DEFAULT = Vivid

        /** Pure rule: the variant kept under [name], [LIST_DEFAULT] when it is unknown. */
        fun of(name: String?): GlobeLook = entries.firstOrNull { it.name == name } ?: LIST_DEFAULT
    }
}

/** Where the variant of the globe of the list is kept: on this phone only, never sent anywhere. */
object GlobeLookStore {
    private const val FILE = "trip_map_settings"
    private const val KEY = "globe_look"

    fun read(context: Context): GlobeLook = runCatching {
        GlobeLook.of(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
    }.getOrDefault(GlobeLook.LIST_DEFAULT)

    fun save(context: Context, look: GlobeLook) {
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, look.name).apply() }
    }
}
