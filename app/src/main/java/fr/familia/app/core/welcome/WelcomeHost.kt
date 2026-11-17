package fr.familia.app.core.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What the first opening knows before the welcome: the family, and whether its module shows. */
internal sealed interface WelcomeChoice {
    data object Waiting : WelcomeChoice
    data object Skip : WelcomeChoice
    /** Read in time, and the module is off for me: the next opening waits less ([WELCOME_OFF_WAIT_MILLIS]). */
    data object Off : WelcomeChoice
    data class Play(val family: Family) : WelcomeChoice
}

/** How long the first opening waits for the family and config/app before going on without the welcome. */
internal const val WELCOME_WAIT_MILLIS = 2_500L

/**
 * Once the module was read off on this phone: the copy on the phone answers in this time, so each
 * opening never shows an empty screen longer, and the welcome still plays as soon as it is on.
 */
internal const val WELCOME_OFF_WAIT_MILLIS = 500L

/**
 * The welcome when its module shows for me ([Feature.OpeningAnimation]), else straight on. It waits
 * at most [WELCOME_WAIT_MILLIS] for the family and the modules: offline or slow, the app goes on and
 * the welcome stays for the next opening. [onPlayed] is called as soon as it starts, so that a phone
 * stopped halfway never plays it again by itself; [onDone] once it is over or skipped.
 */
@Composable
fun WelcomeHost(
    family: Flow<Family>?,
    config: Flow<AppConfig>?,
    me: Member,
    onPlayed: () -> Unit,
    onDone: () -> Unit,
    photos: Map<String, ImageBitmap> = emptyMap(),
    /** How long to wait for the family and the modules: shorter once the module was seen off. */
    waitMillis: Long = WELCOME_WAIT_MILLIS,
    /** The module was read off for me (note C de #334): remembered on the phone. */
    onModuleOff: () -> Unit = {},
) {
    val done by rememberUpdatedState(onDone)
    val played by rememberUpdatedState(onPlayed)
    val off by rememberUpdatedState(onModuleOff)
    var choice by remember(family, config) { mutableStateOf<WelcomeChoice>(WelcomeChoice.Waiting) }
    LaunchedEffect(family, config) {
        choice = if (family == null || config == null) WelcomeChoice.Skip else chooseWelcome(family, config, me, waitMillis)
    }
    when (val shown = choice) {
        WelcomeChoice.Waiting -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WelcomeChoice.Skip -> LaunchedEffect(Unit) { done() }
        WelcomeChoice.Off -> LaunchedEffect(Unit) {
            off()
            done()
        }
        is WelcomeChoice.Play -> {
            LaunchedEffect(Unit) { played() }
            WelcomeAnimation(shown.family, me, photos) { done() }
        }
    }
}

/** Play when the family and config/app come in time and the module shows for me; else skip. */
internal suspend fun chooseWelcome(family: Flow<Family>, config: Flow<AppConfig>, me: Member, waitMillis: Long = WELCOME_WAIT_MILLIS): WelcomeChoice {
    val both = try {
        withTimeoutOrNull(waitMillis) { combine(family, config) { f, c -> f to c }.first() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } ?: return WelcomeChoice.Skip
    val (known, app) = both
    val flags = FeatureFlags(app.features, isOwner = known.ownerId != null && known.ownerId == me.id)
    val mine = known.members.firstOrNull { it.id == me.id }
    return when {
        mine == null || known.members.isEmpty() -> WelcomeChoice.Skip
        flags.isOn(Feature.OpeningAnimation) -> WelcomeChoice.Play(known)
        else -> WelcomeChoice.Off
    }
}
