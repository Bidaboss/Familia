package fr.familia.app.feature.trips.map

import androidx.compose.runtime.saveable.Saver

/**
 * Where the camera of a map was the last time it came to rest (règle 25: « Retour » puts me back
 * where I was). Kept with the screen, so it comes back after a page opened from the map, or a turn
 * of the phone. Plain numbers, so that it fits in the saved state of the screen.
 */
class CameraMemory(camera: DoubleArray? = null) {
    /** longitude, latitude, zoom, bearing, pitch; null before the first rest. */
    var camera: DoubleArray? = camera?.takeIf { it.size == SIZE }
        private set

    fun keep(lng: Double, lat: Double, zoom: Double, bearing: Double, pitch: Double) {
        camera = doubleArrayOf(lng, lat, zoom, bearing, pitch)
    }

    companion object {
        private const val SIZE = 5

        /** Read at the moment the screen is saved, so it holds the last rest of the camera. */
        val Saver: Saver<CameraMemory, DoubleArray> = Saver(save = { it.camera }, restore = { CameraMemory(it) })
    }
}
