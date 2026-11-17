package fr.familia.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The longest a screen may take to settle before taps go through again, whatever happens. */
internal const val TAP_GUARD_MAX_MS = 1_000L

/**
 * A double tap opens one screen, not two (démo du 21). While a screen opens or closes, the one
 * that leaves still shows and would take the second tap: until the new screen is settled
 * (RESUMED, at the end of its animation), taps are held here. Never more than [TAP_GUARD_MAX_MS],
 * so a screen that does not settle never blocks the app.
 */
@Composable
fun BoxScope.TapGuardWhileNavigating(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        settling = true
        withTimeoutOrNull(TAP_GUARD_MAX_MS) {
            current.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        }
        settling = false
    }
    if (settling) {
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        )
    }
}
