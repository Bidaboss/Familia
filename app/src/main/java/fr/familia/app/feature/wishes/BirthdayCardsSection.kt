package fr.familia.app.feature.wishes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.viewas.LocalViewAs
import java.time.LocalDate
import java.time.ZoneId

/** « Cartes de vœux » on a member's page (récit 36), while its module is on. Shows nothing on an ordinary day. */
@Composable
fun BirthdayCardsSection(container: AppContainer?, currentMember: Member, person: Member, year: Int? = null) {
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    val viewingAs = LocalViewAs.current.viewed != null
    val viewModel: BirthdayCardsViewModel = viewModel(
        key = "birthdayCards-${person.id}-$today-$year-$viewingAs",
        factory = viewModelFactory {
            initializer {
                BirthdayCardsViewModel(
                    container?.birthdayCardRepository, container?.goodNewsRepository, currentMember, person, today, mayWrite = !viewingAs, birthdayYear = year,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BirthdayCardsContent(state, person.firstName, viewModel::send, viewModel::remove)
}

@Composable
fun BirthdayCardsContent(state: BirthdayCardsState, firstName: String, onSend: (String) -> Unit, onRemove: () -> Unit) {
    if (state.isEmpty) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.wishes_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (state.canWrite) Composer(state, firstName, onSend, onRemove)
        state.cards.forEach { WishCard(it) }
    }
}

@Composable
fun WishCard(card: BirthdayCard) {
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday) {
        Text(card.text, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.wishes_from, card.authorName),
            style = MaterialTheme.typography.labelLarge,
            color = FamiliaClay.colors.onTintVariant(ClayTint.Birthday),
        )
    }
}

@Composable
private fun Composer(state: BirthdayCardsState, firstName: String, onSend: (String) -> Unit, onRemove: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    // My card already sent: its text, to change it.
    LaunchedEffect(state.mine?.text) { state.mine?.let { text = it.text } }
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday) {
        Text(stringResource(R.string.wishes_write, firstName), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= BirthdayCard.TEXT_MAX) text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.wishes_field)) },
            minLines = 2,
        )
        if (state.failed) {
            Text(stringResource(R.string.wishes_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (state.mine != null) {
                TextButton(onClick = onRemove, enabled = !state.saving, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.wishes_remove))
                }
            }
            FilledTonalButton(
                onClick = { onSend(text) },
                enabled = text.isNotBlank() && !state.saving && text.trim() != state.mine?.text,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (state.mine == null) R.string.wishes_send else R.string.wishes_change))
            }
        }
    }
}
