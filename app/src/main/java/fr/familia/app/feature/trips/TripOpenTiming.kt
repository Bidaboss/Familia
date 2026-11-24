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

    /** The steps already written for this touch (C1 of #605: a style reloaded wrote « carte » twice). */
    private val written = java.util.Collections.synchronizedSet(HashSet<String>())

    /** The touch on a trip (list, globe or link). */
    fun touched(now: Long = SystemClock.elapsedRealtime()) {
        written.clear()
        touchedAt = now
    }

    /** A step of the opening: « écran », « carte »; once per touch for each. */
    fun reached(what: String, now: Long = SystemClock.elapsedRealtime()) {
        val since = elapsed(touchedAt, now) ?: return
        if (!firstTime(written, what)) return
        Log.i("TripOpen", "$what : $since ms après le toucher")
    }

    /** Pure rule: true the first time [what] is reached for a touch, and keeps it in [written]. */
    fun firstTime(written: MutableSet<String>, what: String): Boolean = written.add(what)

    /** Pure rule: the time since the touch, null when there was no touch or it is older than a minute. */
    fun elapsed(touchedAt: Long, now: Long): Long? =
        (now - touchedAt).takeIf { touchedAt > 0L && it in 0..60_000L }
}
