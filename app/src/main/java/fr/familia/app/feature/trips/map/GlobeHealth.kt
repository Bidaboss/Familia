package fr.familia.app.feature.trips.map

import android.content.Context

/**
 * Short pause of the maps after a failure (retour d'essai du 24/11): the globe of the list, the
 * flight of « Lancer le voyage » and the globe of the destination are not offered again for
 * [PAUSE_MILLIS], so a phone without network does not wait for them each time. Kept in memory
 * only: the next launch of the app tries again, and a success clears it. The home of a trip and
 * the screens over its map do not look at it: they try again each time they open.
 */
object GlobeHealth {
    const val PAUSE_MILLIS = 3 * 60 * 1000L

    @Volatile
    private var failedAt = 0L

    @Suppress("UNUSED_PARAMETER")
    fun usable(context: Context, now: Long = System.currentTimeMillis()): Boolean = usable(failedAt, now)

    /** Pure rule: usable unless a failure is recent. */
    fun usable(failedAt: Long, now: Long): Boolean = failedAt == 0L || now - failedAt >= PAUSE_MILLIS || now < failedAt

    @Suppress("UNUSED_PARAMETER")
    fun markFailed(context: Context, now: Long = System.currentTimeMillis()) {
        failedAt = now
    }

    @Suppress("UNUSED_PARAMETER")
    fun markWorking(context: Context) {
        failedAt = 0L
    }
}
