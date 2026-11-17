package fr.familia.app.feature.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.widget.Toast
import fr.familia.app.R
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.PresentationLength
import fr.familia.app.core.model.PresentationMode
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The mode présentation of the admin principal (récit 48): reads config/presentation, turns it on or off. */
class PresentationViewModel(
    private val config: AppConfigRepository?,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    val mode: StateFlow<PresentationMode?> = (config?.observePresentation() ?: emptyFlow())
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _failed = MutableStateFlow(false)

    /** True when the server refused the change: the screen says so once. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun start(length: PresentationLength) = write {
        val now = clock()
        startPresentation(length.until(now), now)
    }

    fun stop() = write { stopPresentation() }

    fun failureShown() {
        _failed.value = false
    }

    private fun write(block: suspend AppConfigRepository.() -> Unit) {
        val repo = config ?: return
        viewModelScope.launch {
            try {
                repo.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failed.value = true
            }
        }
    }
}

/** The mode and its actions, for the admin principal only; null for everyone else. */
class PresentationState(val mode: PresentationMode?, val now: Instant, val onStart: (PresentationLength) -> Unit, val onStop: () -> Unit) {
    val isOn: Boolean get() = mode?.isOn(now) == true
}

/**
 * The mode présentation, live, for the admin principal ([isOwner]); null for the others, who never
 * read config/presentation (the rules refuse it). The clock ticks every 30 s, so the banner goes
 * at the end of the mode by itself.
 */
@Composable
fun rememberPresentation(container: AppContainer?, isOwner: Boolean): PresentationState? {
    if (!isOwner || container?.appConfigRepository == null) return null
    val viewModel: PresentationViewModel = viewModel(
        key = "presentation",
        factory = viewModelFactory { initializer { PresentationViewModel(container.appConfigRepository) } },
    )
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val refused = stringResource(R.string.presentation_failed)
    LaunchedEffect(failed) {
        if (failed) {
            Toast.makeText(context, refused, Toast.LENGTH_LONG).show()
            viewModel.failureShown()
        }
    }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    return PresentationState(mode, now, viewModel::start, viewModel::stop)
}

private fun endLabel(mode: PresentationMode): String? =
    mode.until?.let { formatTime(it.atZone(ZoneId.of("Europe/Paris")).toLocalTime()) }

/** « Mode présentation · jusqu'à 16 h 30 » above every tab of the admin principal, with « Éteindre ». */
@Composable
fun PresentationBanner(state: PresentationState, modifier: Modifier = Modifier) {
    val mode = state.mode ?: return
    if (!state.isOn) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Slideshow, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                endLabel(mode)?.let { stringResource(R.string.presentation_banner_until, it) } ?: stringResource(R.string.presentation_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = state.onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.presentation_stop)) }
        }
    }
}

/** The card of Administration: what it does, then 2 h, 4 h or until turned off; « Éteindre » while on. */
@Composable
fun PresentationSection(state: PresentationState, modifier: Modifier = Modifier) {
    ClayCard(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClayCardTitle(stringResource(R.string.presentation_title), style = MaterialTheme.typography.titleMedium)
            val mode = state.mode
            if (state.isOn && mode != null) {
                Text(
                    endLabel(mode)?.let { stringResource(R.string.presentation_on_until, it) } ?: stringResource(R.string.presentation_on),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = state.onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.presentation_stop))
                }
            } else {
                Text(stringResource(R.string.presentation_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { state.onStart(PresentationLength.TwoHours) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.presentation_two_hours))
                    }
                    OutlinedButton(onClick = { state.onStart(PresentationLength.FourHours) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.presentation_four_hours))
                    }
                }
                OutlinedButton(onClick = { state.onStart(PresentationLength.UntilOff) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.presentation_until_off))
                }
            }
        }
    }
}
