package fr.familia.app.feature.update

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.FamiliaPreviews

/** "Vérifier les mises à jour", opened from the Famille tab. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckScreen(
    state: UpdateCheckUiState,
    installedVersion: String,
    onCheck: () -> Unit,
    onUpdate: (url: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.update_check_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.update_check_installed, installedVersion), style = MaterialTheme.typography.bodyLarge)
            when (state) {
                UpdateCheckUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.update_check_in_progress), style = MaterialTheme.typography.bodyLarge)
                }
                UpdateCheckUiState.UpToDate ->
                    Outcome(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.update_check_up_to_date))
                is UpdateCheckUiState.Available -> {
                    Outcome(
                        Icons.Outlined.SystemUpdate,
                        MaterialTheme.colorScheme.primary,
                        stringResource(R.string.update_available, state.update.versionName),
                    )
                    Text(stringResource(R.string.update_help), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { onUpdate(state.update.downloadUrl) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.update_action)) }
                }
                UpdateCheckUiState.Offline ->
                    Outcome(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.error, stringResource(R.string.update_check_offline))
                UpdateCheckUiState.Failed ->
                    Outcome(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, stringResource(R.string.update_check_failed))
            }
            OutlinedButton(
                onClick = onCheck,
                enabled = state != UpdateCheckUiState.Checking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.update_check_again)) }
        }
    }
}

/** The result, read aloud by TalkBack as soon as it appears. */
@Composable
private fun Outcome(icon: ImageVector, color: Color, text: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@FamiliaPreviews
@Composable
private fun UpdateCheckScreenPreview() {
    FamiliaTheme {
        Surface {
            UpdateCheckScreen(
                UpdateCheckUiState.Available(AppUpdate("0.2.0", "https://example.com")),
                installedVersion = "0.1.0",
                onCheck = {},
                onUpdate = {},
                onBack = {},
            )
        }
    }
}
