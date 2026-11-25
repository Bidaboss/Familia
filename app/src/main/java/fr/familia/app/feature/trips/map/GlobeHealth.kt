package fr.familia.app.feature.trips.map

import android.content.Context

/**
 * Silent fallback of the globe (récit 94, temps 5): when the globe failed to start on this phone
 * (no network, SDK error, style not loaded in time), the globe is not offered again for a while and
 * the drawn map stays. A success clears it. Stored per phone, never sent anywhere.
 */
object GlobeHealth {
    private const val FILE = "globe_health"
    private const val KEY = "failed_at"
    const val PAUSE_MILLIS = 6 * 60 * 60 * 1000L

    fun usable(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        usable(runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY, 0L) }.getOrDefault(0L), now)

    /** Pure rule: usable unless a failure is recent. */
    fun usable(failedAt: Long, now: Long): Boolean = failedAt == 0L || now - failedAt >= PAUSE_MILLIS || now < failedAt

    fun markFailed(context: Context, now: Long = System.currentTimeMillis()) {
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY, now).apply() }
    }

    fun markWorking(context: Context) {
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
    }
}
