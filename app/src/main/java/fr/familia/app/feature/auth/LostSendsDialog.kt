package fr.familia.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.SendEntry
import fr.familia.app.core.data.SendKind

/** The creations the server never received, said once after sign-in (note C de #331). */
@Composable
fun LostSendsDialog(lost: List<SendEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.send_lost_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.send_lost_body))
                lost.forEach { Text(lostLabel(it), style = MaterialTheme.typography.bodyLarge) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun lostLabel(entry: SendEntry): String = stringResource(
    when (entry.kind) {
        SendKind.HelpRequest -> R.string.send_lost_help
        SendKind.GoodNews -> R.string.send_lost_good_news
        SendKind.GoodDeal -> R.string.send_lost_good_deal
        SendKind.LendItem -> R.string.send_lost_lend
    },
    entry.title,
)
