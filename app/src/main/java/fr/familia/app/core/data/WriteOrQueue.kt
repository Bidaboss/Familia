package fr.familia.app.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a button waits for the server before it lets the write wait for the network. */
const val WRITE_WAIT_MILLIS: Long = 4_000

/**
 * Starts [write] in [scope] and answers once it is done, or after [timeoutMillis] while it waits
 * for the network in Firestore's cache (offline it shows there at once, and leaves when the network
 * is back): true then, so a button never stays « en cours » for ever. False when it failed in that
 * time. A refusal that comes later, once the network is back, calls [onLateFailure] while [scope]
 * is alive: Firestore takes the refused write back out of the cache, and the screen says so rather
 * than letting it vanish without a word.
 */
suspend fun writeOrQueue(
    scope: CoroutineScope,
    timeoutMillis: Long = WRITE_WAIT_MILLIS,
    onLateFailure: () -> Unit,
    write: suspend () -> Unit,
): Boolean {
    val result = CompletableDeferred<Boolean>()
    scope.launch {
        val ok = try {
            write()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!result.complete(ok) && !ok) onLateFailure()
    }
    return withTimeoutOrNull(timeoutMillis) { result.await() }
        ?: if (result.complete(true)) true else result.await()
}
