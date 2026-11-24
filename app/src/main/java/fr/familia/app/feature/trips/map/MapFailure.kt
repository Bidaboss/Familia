package fr.familia.app.feature.trips.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fr.familia.app.BuildConfig
import fr.familia.app.core.crash.CrashCatcher
import fr.familia.app.core.crash.PendingCrashStore
import fr.familia.app.core.crash.pendingCrash

/**
 * Why the satellite map did not show (retour d'essai du 24/11: never a silent cut). Each reason is
 * its own kind in « Plantages » (Administration), so the admin can copy it: the class name says
 * the reason, the screen field says where and the detail given by Mapbox (never a typed text).
 */
sealed class MapUnavailable(val detail: String, cause: Throwable? = null) : Exception(null, cause) {
    /** No public token in this APK (« APK de publication » without the secret MAPBOX_PUBLIC_TOKEN). */
    class NoToken : MapUnavailable("jeton absent")

    /** A token that is not a public one (pk.). */
    class BadToken : MapUnavailable("jeton non public")

    /** The phone had no network when the map gave up. */
    class NoNetwork(detail: String) : MapUnavailable(detail)

    /** Mapbox said the style could not load ([detail]: its type and message, 401 for a refused token). */
    class SdkError(detail: String) : MapUnavailable(detail)

    /** Nothing drawn in time ([detail]: the delay, and the last error Mapbox gave, if any). */
    class Timeout(detail: String) : MapUnavailable(detail)

    /** The view of the map could not even start: its own error is the cause. */
    class ViewFailed(cause: Throwable) : MapUnavailable(cause.javaClass.simpleName, cause)
}

/** How long the map waits for its style before it gives up, on a slow phone too. */
const val MAP_STYLE_TIMEOUT_MS = 15_000L

/**
 * The reason of a failure, from what is known when it happens. Pure: the token, the network, the
 * last error of Mapbox ([sdkError], « STYLE: … »), the view's own error, and whether time ran out.
 */
fun mapFailureReason(token: String, online: Boolean, sdkError: String?, viewError: Throwable?, timedOut: Boolean): MapUnavailable = when {
    token.isBlank() -> MapUnavailable.NoToken()
    !token.startsWith("pk.") -> MapUnavailable.BadToken()
    viewError != null -> MapUnavailable.ViewFailed(viewError)
    !online -> MapUnavailable.NoNetwork(listOfNotNull(if (timedOut) "délai ${MAP_STYLE_TIMEOUT_MS / 1000} s" else null, sdkError).joinToString(", ").ifEmpty { "hors ligne" })
    sdkError != null && !timedOut -> MapUnavailable.SdkError(sdkError)
    else -> MapUnavailable.Timeout(listOfNotNull("délai ${MAP_STYLE_TIMEOUT_MS / 1000} s", sdkError?.let { "dernière erreur $it" }).joinToString(", "))
}

/** The error of Mapbox for « Plantages »: its type and message, without any token of an address (C3 of #573), 50 characters at most. */
fun mapErrorLine(type: String, message: String): String =
    "$type: ${message.replace(Regex("access_token=[^&\\s]*"), "access_token=…")}".take(50)

/** The line of « Plantages » that says where: « Carte TripDetail : délai 15 s ». */
fun mapFailureScreen(where: String, failure: MapUnavailable): String =
    "Carte ${where.ifBlank { "?" }} : ${failure.detail}".take(80)

object MapFailureLog {
    /** Once per kind and screen while the app runs: a map retried ten times is one report. */
    private val written = HashSet<String>()

    fun online(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /**
     * Keeps [failure] on the phone with the crashes: it reaches « Plantages » at the next launch.
     * A test APK has no token on purpose (no Drive folder either): that is not a failure to report.
     */
    fun record(context: Context, failure: MapUnavailable, where: String = CrashCatcher.screen) {
        if (failure is MapUnavailable.NoToken && BuildConfig.DOWNLOAD_URL.isBlank()) return
        val key = failure.javaClass.name + "|" + where
        synchronized(written) { if (!written.add(key)) return }
        runCatching { PendingCrashStore(context.applicationContext).add(pendingCrash(failure, mapFailureScreen(where, failure), System.currentTimeMillis())) }
    }
}
