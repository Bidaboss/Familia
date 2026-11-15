package fr.familia.app.core.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** What a form tells the family once it closes (audit DESIGN du 14/11). */
enum class Confirmation(@StringRes val textRes: Int) {
    Saved(R.string.confirm_saved),
    Deleted(R.string.confirm_deleted),
    MemberRemoved(R.string.confirm_member_removed),

    /** Offline: Firestore keeps the write and sends it when the network is back. */
    Queued(R.string.confirm_queued),
}

/** How long the message stays: long enough to read slowly, shorter than the default « long ». */
const val CONFIRMATION_MILLIS = 8_000L

/** The message area of FamiliaApp, so a screen can keep its button above the message. */
val LocalSnackbarHost = staticCompositionLocalOf { SnackbarHostState() }

/** Shows [message] for [CONFIRMATION_MILLIS], replacing the one on screen. */
suspend fun SnackbarHostState.showConfirmation(message: String) {
    currentSnackbarData?.dismiss()
    withTimeoutOrNull(CONFIRMATION_MILLIS) { showSnackbar(message, duration = SnackbarDuration.Indefinite) }
}

/** Lifts a bottom button (« Ajouter ») above the message while one shows, so it is never hidden. */
@Composable
fun Modifier.aboveSnackbar(): Modifier {
    val shown = LocalSnackbarHost.current.currentSnackbarData != null
    val lift by animateDpAsState(if (shown) 72.dp else 0.dp, label = "aboveSnackbar")
    return padding(bottom = lift)
}

/** A write the server took ([Accepted]) or that waits in the offline queue ([Queued]). */
enum class WriteSent {
    Accepted,
    Queued,
    ;

    /** The message to show: [done] once accepted, the offline one while queued. */
    fun or(done: Confirmation): Confirmation = if (this == Queued) Confirmation.Queued else done
}

/**
 * Runs [block], a Firestore write. Offline, Firestore only confirms a write when the server has
 * it, and keeps it queued until then: still pending after [timeoutMillis] means [WriteSent.Queued].
 * Null when the server refused the write.
 */
suspend fun sendWrite(timeoutMillis: Long, block: suspend () -> Unit): WriteSent? = try {
    if (withTimeoutOrNull(timeoutMillis) { block() } == null) WriteSent.Queued else WriteSent.Accepted
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
