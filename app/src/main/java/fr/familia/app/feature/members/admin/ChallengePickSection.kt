package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.feature.tree.ChallengeCatalog
import fr.familia.app.feature.tree.MonthPick
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.familia.app.core.ui.ClaySpacing

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

private fun monthLabel(month: YearMonth): String = month.format(monthFormat).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/**
 * « Défi du mois » in Administration (récit 35): the admin principal picks the challenge of this
 * month, while nobody has moved its gauge, and of the next one, from the list.
 */
@Composable
fun ChallengePickSection(picks: List<MonthPick>, onPick: (YearMonth, String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        picks.forEach { pick ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(monthLabel(pick.month), style = MaterialTheme.typography.titleMedium)
                Text("${pick.choice.emoji} ${pick.choice.title}", style = MaterialTheme.typography.bodyLarge)
                if (pick.locked) {
                    Text(
                        stringResource(R.string.administration_challenge_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = { choosing = pick.month.toString() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.administration_challenge_change, monthLabel(pick.month))) }
                }
            }
        }
    }
    val pick = picks.firstOrNull { it.month.toString() == choosing && !it.locked }
    if (pick != null) {
        ChallengeChoiceDialog(
            pick = pick,
            onConfirm = { id -> onPick(pick.month, id); choosing = null },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun ChallengeChoiceDialog(pick: MonthPick, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(pick.choice.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.administration_challenge_dialog_title, monthLabel(pick.month))) },
        text = {
            // The radios became pills (refonte, lot 3): one group, read as radios by TalkBack.
            ChipGroup(Modifier.verticalScroll(rememberScrollState())) {
                ChallengeCatalog.choices.forEach { choice ->
                    FormChip(
                        label = "${choice.emoji} ${choice.title}",
                        selected = selected == choice.id,
                        onClick = { selected = choice.id },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.administration_challenge_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
