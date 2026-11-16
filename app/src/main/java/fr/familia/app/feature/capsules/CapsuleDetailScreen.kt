package fr.familia.app.feature.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** One capsule: who wrote it for whom, the day it opens, the message and its photos. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleDetailScreen(state: CapsuleDetailUiState, onRemove: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capsule_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Capsules) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CapsuleDetailUiState.Loading, CapsuleDetailUiState.Removed -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                CapsuleDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.capsule_missing_title),
                    body = stringResource(R.string.capsule_missing_body),
                )
                is CapsuleDetailUiState.Ready -> Content(state, onRemove)
            }
        }
    }
}

@Composable
private fun Content(state: CapsuleDetailUiState.Ready, onRemove: () -> Unit) {
    val row = state.row
    val capsule = row.capsule
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(capsuleHeadline(row), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (!row.isMine) Text(stringResource(R.string.capsule_for, row.recipients), style = MaterialTheme.typography.bodyLarge, color = muted)
            Text(capsuleDateLine(capsule), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        if (row.isMine && !capsule.isOpened) {
            // The author reads it at any time; the recipients do not even know it exists yet.
            Text(stringResource(R.string.capsule_sealed_note), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(capsule.text, style = MaterialTheme.typography.bodyLarge)
        }
        (1..capsule.photoCount).forEach { n ->
            StorageImage(
                path = capsule.photoPath(n),
                contentDescription = stringResource(R.string.capsule_photo, n, capsule.photoCount),
                // StorageImage takes the size it is given: a 4:3 frame, the photo fitted inside.
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                contentScale = ContentScale.Fit,
            )
        }
        if (row.isMine) {
            if (state.removeFailed) {
                Text(stringResource(R.string.capsule_remove_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            RemoveButton(onRemove)
        }
    }
}

@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.capsule_remove)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.capsule_remove_title)) },
            text = { Text(stringResource(R.string.capsule_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.capsule_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** Camille's sealed capsule for the children, without photos so nothing loads in screenshots. */
internal fun previewCapsuleDetail(): CapsuleDetailUiState.Ready =
    CapsuleDetailUiState.Ready(CapsuleRow(previewCapsules()[1], "Noé et Lou", isMine = true))

@FamiliaPreviews
@Composable
private fun CapsuleDetailPreview() {
    FamiliaTheme { Surface { CapsuleDetailScreen(previewCapsuleDetail(), {}, {}) } }
}
