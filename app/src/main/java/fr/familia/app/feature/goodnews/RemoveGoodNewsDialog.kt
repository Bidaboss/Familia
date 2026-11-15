package fr.familia.app.feature.goodnews

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R

/** « Retirer cette Bonne nouvelle ? », asked before anything is removed. */
@Composable
fun RemoveGoodNewsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goodnews_remove_title)) },
        text = { Text(stringResource(R.string.goodnews_remove_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.goodnews_remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** « Ça n'a pas marché », after a reaction or a removal that failed. */
@Composable
fun GoodNewsActionFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goodnews_action_failed_title)) },
        text = { Text(stringResource(R.string.goodnews_action_failed_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
