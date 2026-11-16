package fr.familia.app.feature.thinkofme

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
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Moment
import fr.familia.app.core.model.MomentCheer
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.trips.TripDateButton
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLabel = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** « Pensez à moi » on a member's page (récit 43), while its module is on. */
@Composable
fun ThinkOfMeSection(container: AppContainer?, currentMember: Member, person: Member) {
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    val viewingAs = LocalViewAs.current.viewed != null
    val viewModel: ThinkOfMeViewModel = viewModel(
        key = "thinkOfMe-${person.id}-$today-$viewingAs",
        factory = viewModelFactory {
            initializer { ThinkOfMeViewModel(container?.momentRepository, currentMember, person, today, mayWrite = !viewingAs) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThinkOfMeContent(
        state, person.firstName, today, viewModel.notedForTeen,
        onNote = viewModel::note, onAccept = viewModel::accept, onRemove = viewModel::remove,
        onCheer = viewModel::cheer, onRemoveCheer = viewModel::removeCheer,
    )
}

@Composable
fun ThinkOfMeContent(
    state: ThinkOfMeState,
    firstName: String,
    today: LocalDate,
    notedForTeen: Boolean,
    onNote: (String, LocalDate) -> Unit,
    onAccept: (Moment) -> Unit,
    onRemove: (Moment) -> Unit,
    onCheer: (String) -> Unit,
    onRemoveCheer: () -> Unit,
) {
    if (state.isEmpty) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.think_of_me_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        state.today?.let { moment ->
            ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday) {
                Text(stringResource(R.string.think_of_me_today, moment.title), style = MaterialTheme.typography.titleMedium)
                if (state.canCheer) Cheer(state, firstName, onCheer, onRemoveCheer)
            }
            state.cheers.forEach { CheerCard(it) }
        }
        if (state.canManage) {
            state.upcoming.forEach { UpcomingLine(it, state, firstName, onAccept, onRemove) }
            NoteForm(state, today, notedForTeen, firstName, onNote)
        }
        if (state.failed) {
            Text(stringResource(R.string.think_of_me_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Cheer(state: ThinkOfMeState, firstName: String, onCheer: (String) -> Unit, onRemoveCheer: () -> Unit) {
    val mine = state.mine
    if (mine != null) {
        ClayLink(stringResource(R.string.think_of_me_cheer_remove), onClick = onRemoveCheer)
        return
    }
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= MomentCheer.TEXT_MAX) text = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.think_of_me_cheer_field, firstName)) },
        minLines = 2,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        FilledTonalButton(
            onClick = { onCheer(text); text = "" },
            enabled = text.isNotBlank() && !state.saving,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.think_of_me_cheer_send)) }
    }
}

@Composable
private fun CheerCard(cheer: MomentCheer) {
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday) {
        Text(cheer.text, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.wishes_from, cheer.authorName),
            style = MaterialTheme.typography.labelLarge,
            color = FamiliaClay.colors.onTintVariant(ClayTint.Birthday),
        )
    }
}

@Composable
private fun UpcomingLine(moment: Moment, state: ThinkOfMeState, firstName: String, onAccept: (Moment) -> Unit, onRemove: (Moment) -> Unit) {
    ClayCard(Modifier.fillMaxWidth()) {
        Text(moment.title, style = MaterialTheme.typography.titleMedium)
        Text(
            dayLabel.format(moment.day).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
        )
        when {
            moment.pending && state.canAccept -> {
                Text(stringResource(R.string.think_of_me_noted_by, moment.authorName), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { onAccept(moment) }, enabled = !state.saving, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.think_of_me_accept))
                    }
                    TextButton(onClick = { onRemove(moment) }, enabled = !state.saving, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.think_of_me_refuse))
                    }
                }
            }
            else -> {
                if (moment.pending) {
                    Text(stringResource(R.string.think_of_me_waiting, firstName), style = MaterialTheme.typography.bodyMedium)
                }
                ClayLink(stringResource(R.string.think_of_me_remove), onClick = { if (!state.saving) onRemove(moment) })
            }
        }
    }
}

@Composable
private fun NoteForm(state: ThinkOfMeState, today: LocalDate, notedForTeen: Boolean, firstName: String, onNote: (String, LocalDate) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    ClayCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.think_of_me_note_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= Moment.TITLE_MAX) title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.think_of_me_note_field)) },
            singleLine = true,
        )
        TripDateButton(day, stringResource(R.string.think_of_me_note_day), onPick = { if (!it.isBefore(today)) day = it }, initial = today)
        if (notedForTeen) {
            Text(stringResource(R.string.think_of_me_teen_hint, firstName), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                onClick = {
                    day?.let { onNote(title, it) }
                    title = ""
                    day = null
                },
                enabled = title.isNotBlank() && day != null && !state.saving,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.think_of_me_note_action)) }
        }
    }
}
