package fr.familia.app.core.place

import android.content.Context
import fr.familia.app.core.crash.PendingCrashStore
import fr.familia.app.core.crash.pendingCrash

/** Why the photo of a place did not show (retour du 24/11 soir): its own kind in « Plantages ». */
class PhotoUnavailable(detail: String) : Exception(detail)

/**
 * Keeps the reason a Wikimedia photo was not shown with the crashes, so it reaches « Plantages »
 * at the next launch (the drawing showed instead, silently, before). Once per reason while the app
 * runs. The line names the code and the site, never the place of the trip.
 */
object PhotoFailureLog {
    private val written = HashSet<String>()

    /**
     * True for a real failure (refusal, timeout, image error); false for the plain « aucune photo » answer
     * and for a phone without network (retour du 25/11 soir : UnknownHostException, the name of the site
     * could not be looked up), where the drawing is the right thing to show and there is nothing to fix.
     */
    fun isReportable(line: String): Boolean {
        if (line.startsWith("aucune photo")) return false
        // « UnknownHostException » from the search, « image UnknownHostException: Unable to… » from PlaceCover.
        val error = line.removePrefix("image ").substringBefore(':').trim()
        return error !in NO_NETWORK
    }

    /** The errors of a phone offline or between two networks: Wi-Fi lost, plane mode, no signal. */
    private val NO_NETWORK = setOf("UnknownHostException", "ConnectException", "NoRouteToHostException")

    fun record(context: Context, detail: String) {
        val line = detail.take(60)
        synchronized(written) { if (!written.add(line)) return }
        android.util.Log.w("PlacePhotos", "photo non affichée : $line")
        // « Aucune photo » is a normal answer (a place nobody photographed): logged, not shown as a crash.
        if (!isReportable(line)) return
        runCatching {
            PendingCrashStore(context.applicationContext).add(pendingCrash(PhotoUnavailable(line), "Photo du lieu : $line".take(80), System.currentTimeMillis()))
        }
    }
}
