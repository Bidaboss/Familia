package fr.familia.app.feature.export

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** Admin backup screen, opened from the Famille tab (PRD, récit 6 ter). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(state: ExportUiState, onExport: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.export_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.export_body), style = MaterialTheme.typography.bodyLarge)
            val exporting = state == ExportUiState.Exporting
            Button(
                onClick = onExport,
                enabled = !exporting && state != ExportUiState.Picking && state !is ExportUiState.AwaitingDestination,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (exporting) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.export_in_progress))
                } else {
                    Text(stringResource(R.string.export_action))
                }
            }
            when (state) {
                ExportUiState.Done -> Outcome(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, R.string.export_done)
                ExportUiState.Failed -> Outcome(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, R.string.export_failed)
                ExportUiState.Offline -> Outcome(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.error, R.string.export_offline)
                else -> Unit
            }
        }
    }
}

/** The result, read aloud by TalkBack as soon as it appears. */
@Composable
private fun Outcome(icon: ImageVector, color: Color, textRes: Int) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@FamiliaPreviews
@Composable
private fun ExportScreenPreview() {
    FamiliaTheme { Surface { ExportScreen(ExportUiState.Done, onExport = {}, onBack = {}) } }
}
