package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/**
 * « Ajout rapide » during the trip (récit 104, règle 35, DESIGN-90 A): what is added goes under the
 * destination of the day, [destination], without a notice. A null action is not offered.
 */
class QuickAdd(
    val destination: String,
    /** Null when this member has no photo place left. */
    val onPhoto: (() -> Unit)?,
    val onWord: (() -> Unit)?,
    /** « Note vocale » (partie 3b): null when the notes cannot be sent. */
    val onVoice: (() -> Unit)? = null,
)

/** The round camera button, above « Ajouter une étape », and its sheet: Photo, Mot court. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddButton(quick: QuickAdd) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.quick_add_title, quick.destination)
    // Fiche F1 §5.3: a white round with a shadow, the camera in dark ink.
    FloatingActionButton(
        onClick = { open = true },
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = androidx.compose.ui.graphics.Color.White,
        contentColor = androidx.compose.ui.graphics.Color(0xFF17333F),
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = label)
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
                quick.onPhoto?.let { photo ->
                    QuickRow(Icons.Outlined.PhotoCamera, stringResource(R.string.quick_add_photo)) {
                        open = false
                        photo()
                    }
                }
                quick.onVoice?.let { voice ->
                    QuickRow(Icons.Outlined.Mic, stringResource(R.string.quick_add_voice)) {
                        open = false
                        voice()
                    }
                }
                quick.onWord?.let { word ->
                    QuickRow(Icons.Outlined.EditNote, stringResource(R.string.quick_add_word)) {
                        open = false
                        word()
                    }
                }
                Text(
                    stringResource(R.string.quick_add_quiet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
