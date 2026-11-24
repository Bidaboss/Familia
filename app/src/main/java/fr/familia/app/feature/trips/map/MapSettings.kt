package fr.familia.app.feature.trips.map

import android.content.Context

/** The map settings a person keeps (récit 110, décision 155): Satellite or Rues, 2D or 3D. */
data class MapSettings(val look: MapLook = MapLook.Satellite, val tilted: Boolean = false)

/**
 * Where the settings of the trip map are kept: on this phone, for the person who uses it, never
 * sent anywhere. An unknown or missing value gives Satellite in 2D.
 */
object MapSettingsStore {
    private const val FILE = "trip_map_settings"
    private const val LOOK = "look"
    private const val TILTED = "tilted"

    fun read(context: Context): MapSettings = runCatching {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        MapSettings(lookOf(prefs.getString(LOOK, null)), prefs.getBoolean(TILTED, false))
    }.getOrDefault(MapSettings())

    fun save(context: Context, settings: MapSettings) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(LOOK, settings.look.name)
                .putBoolean(TILTED, settings.tilted)
                .apply()
        }
    }

    /** Pure rule: the look saved under [name], Satellite when it is unknown. */
    fun lookOf(name: String?): MapLook = MapLook.entries.firstOrNull { it.name == name } ?: MapLook.Satellite
}
