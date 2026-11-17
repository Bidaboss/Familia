package fr.familia.app.core.crash

import fr.familia.app.core.data.CrashRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * At the launch after a crash, once the member is signed in: each report waiting on the phone is
 * sent, then forgotten. Offline, Firestore keeps the write in its queue and sends it later, so the
 * report is forgotten after 10 s anyway. A report refused (sent before, rules not published yet)
 * is forgotten too: it never loops.
 */
suspend fun sendPendingCrashes(pending: List<PendingCrash>, memberId: String, repository: CrashRepository, forget: (String) -> Unit) {
    for (crash in pending) {
        try {
            withTimeoutOrNull(SEND_TIMEOUT_MS) { repository.send(memberId, crash) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Refused or broken: kept no longer.
        }
        forget(crash.id)
    }
}

private const val SEND_TIMEOUT_MS = 10_000L

/**
 * « Home » for the route « fr.familia.app.feature.home.HomeDestination », « EventDetail » for
 * « …EventDetailDestination/{eventId} »: the name of the screen, never the id it was opened with.
 */
fun crashScreenName(route: String?): String =
    route.orEmpty()
        .substringBefore('/')
        .substringBefore('?')
        .substringAfterLast('.')
        .removeSuffix("Destination")
        .take(80)
