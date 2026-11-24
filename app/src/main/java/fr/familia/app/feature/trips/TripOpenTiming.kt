package fr.familia.app.feature.trips

import android.os.SystemClock
import android.util.Log

/**
 * E-60: how long a trip takes to open, from the touch to its screen, then to its map; written to
 * the log (filter « TripOpen ») so the admin can give the figures. Nothing leaves the phone.
 */
object TripOpenTiming {
    @Volatile
    private var touchedAt = 0L

    /** The touch on a trip (list, globe or link). */
    fun touched(now: Long = SystemClock.elapsedRealtime()) {
        touchedAt = now
    }

    /** A step of the opening: « écran », « carte »; once per touch for each. */
    fun reached(what: String, now: Long = SystemClock.elapsedRealtime()) {
        val since = elapsed(touchedAt, now) ?: return
        Log.i("TripOpen", "$what : $since ms après le toucher")
    }

    /** Pure rule: the time since the touch, null when there was no touch or it is older than a minute. */
    fun elapsed(touchedAt: Long, now: Long): Long? =
        (now - touchedAt).takeIf { touchedAt > 0L && it in 0..60_000L }
}
