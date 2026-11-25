package fr.familia.app.feature.trips.safety

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.phrases.rememberAbroadCountries
import java.util.Locale

/**
 * « Sécurité » of [tripId] (récit 105, règle 43): the emergency numbers of each country abroad,
 * never for France, read by anyone who follows the trip. « Prévenir ma famille » is for those who
 * go.
 */
@Composable
fun TripSafetyRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-safety-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val safety: TripSafetyViewModel = viewModel(
        key = "trip-safety-alert-$tripId",
        factory = viewModelFactory { initializer { TripSafetyViewModel(container?.tripRepository, tripId, currentMember) } },
    )
    val state by detail.uiState.collectAsStateWithLifecycle()
    val alert by safety.alert.collectAsStateWithLifecycle()
    val shown = state as? TripDetailUiState.Success
    val countries = EmergencyNumbers.abroad(rememberAbroadCountries(container, shown?.steps.orEmpty()))
    val goes = shown?.trip?.participantIds?.contains(currentMember.id) == true
    TripSafetyScreen(
        loading = shown == null,
        countries = countries,
        alert = alert,
        onAlert = if (goes && container != null) safety::alertFamily else null,
        onDismissAlert = safety::dismiss,
        onBack = onBack,
    )
}

/** Stateless. [onAlert] is null for someone who follows the trip without going. */
@Composable
fun TripSafetyScreen(
    loading: Boolean,
    countries: List<String>,
    alert: AlertState,
    onAlert: (() -> Unit)?,
    onDismissAlert: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { FamiliaTopBar(title = { Text(stringResource(R.string.safety_title)) }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        if (loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onAlert != null) AlertBlock(alert, onAsk = { confirm = true }, onDismissAlert)
            Text(stringResource(R.string.safety_intro), style = MaterialTheme.typography.bodyLarge)
            if (countries.isEmpty()) {
                Text(stringResource(R.string.safety_none), style = MaterialTheme.typography.bodyLarge)
            }
            countries.forEach { code -> CountryBlock(code) }
        }
    }
    if (confirm && onAlert != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.safety_alert_confirm_title)) },
            text = { Text(stringResource(R.string.safety_alert_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onAlert() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.safety_alert_send), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.safety_alert_cancel))
                }
            },
        )
    }
}

@Composable
private fun AlertBlock(alert: AlertState, onAsk: () -> Unit, onDismissAlert: () -> Unit) {
    Text(stringResource(R.string.safety_alert_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.safety_alert_explain), style = MaterialTheme.typography.bodyLarge)
    val result = when (alert) {
        AlertState.Sent -> R.string.safety_alert_sent
        AlertState.TooSoon -> R.string.safety_alert_too_soon
        AlertState.Offline -> R.string.safety_alert_offline
        AlertState.Failed -> R.string.safety_alert_failed
        AlertState.Idle, AlertState.Sending -> null
    }
    if (result != null) {
        Text(
            stringResource(result),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = onDismissAlert, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.safety_alert_ok)) }
    } else {
        Button(
            onClick = onAsk,
            enabled = alert != AlertState.Sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(if (alert == AlertState.Sending) R.string.safety_alert_sending else R.string.safety_alert_button))
        }
    }
}

@Composable
private fun CountryBlock(code: String) {
    val context = LocalContext.current
    val name = Locale("", code).getDisplayCountry(Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    EmergencyNumbers.of(code).forEach { line ->
        val description = stringResource(R.string.safety_call, line.kind.label, line.number)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) {
                    // The dialer opens with the number typed: one more tap calls, nothing rings by mistake.
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${line.number}")))
                    } catch (_: ActivityNotFoundException) {
                        // A phone without a dialer: the number stays readable on the screen.
                    }
                }
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line.kind.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(line.number, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
