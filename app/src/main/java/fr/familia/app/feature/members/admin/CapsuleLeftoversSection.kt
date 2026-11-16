package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing

/**
 * « Capsules d'anciens membres » (PRD rév. 184): how many sealed capsules each member who left
 * still has. They open on their day, unless the admin principal deletes them, unread, after a
 * confirmation that names the count. Stateless but for the open dialog.
 */
@Composable
internal fun CapsuleLeftoversSection(
    leftovers: List<CapsuleLeftover>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    ClayCard(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClayCardTitle(stringResource(R.string.capsule_leftovers_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.capsule_leftovers_help), style = MaterialTheme.typography.bodyMedium)
            leftovers.forEach { left ->
                Text(
                    pluralStringResource(R.plurals.capsule_leftovers_line, left.count, left.memberName, left.count),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (left.deleteAsked) {
                    Text(
                        stringResource(R.string.capsule_leftovers_deleting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = { confirming = left.memberId },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(pluralStringResource(R.plurals.capsule_leftovers_delete, left.count, left.count)) }
                }
            }
        }
    }
    val asked = leftovers.firstOrNull { it.memberId == confirming && !it.deleteAsked }
    if (asked != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(pluralStringResource(R.plurals.capsule_leftovers_confirm_title, asked.count, asked.count)) },
            text = { Text(pluralStringResource(R.plurals.capsule_leftovers_confirm_body, asked.count, asked.memberName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        onDelete(asked.memberId)
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.capsule_leftovers_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
