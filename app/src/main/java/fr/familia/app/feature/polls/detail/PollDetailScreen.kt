package fr.familia.app.feature.polls.detail

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.Poll
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.polls.closingDayLabel
import fr.familia.app.feature.polls.kindLabel
import fr.familia.app.feature.polls.lastDayOf
import fr.familia.app.feature.polls.optionLabel
import fr.familia.app.feature.polls.shortDateLabel
import java.time.Instant
import java.time.LocalDate

/** One poll: answer, results, and for its author « Terminer » and « Choisir cette date ». Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollDetailScreen(
    state: PollDetailUiState,
    onAnswer: (memberId: String, index: Int, answer: DateAnswer) -> Unit,
    onToggleChoice: (memberId: String, index: Int) -> Unit,
    onSend: (memberId: String) -> Unit,
    onClose: () -> Unit,
    onChooseDate: (index: Int, title: String) -> Unit,
    onDelete: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
    /** « Partager » vers une messagerie (PRD, récit 31); hidden when null. */
    onShare: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.polls_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onShare != null && state is PollDetailUiState.Success) ShareButton(onClick = onShare)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PollDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PollDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Poll,
                    title = stringResource(R.string.poll_unavailable_title),
                    body = stringResource(R.string.poll_unavailable_body),
                )
                is PollDetailUiState.Success -> Content(state, today, onAnswer, onToggleChoice, onSend, onClose, onChooseDate, onDelete, onOpenEvent)
            }
        }
    }
    if ((state as? PollDetailUiState.Success)?.error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            text = { Text(stringResource(R.string.poll_action_failed)) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Content(
    state: PollDetailUiState.Success,
    today: LocalDate,
    onAnswer: (String, Int, DateAnswer) -> Unit,
    onToggleChoice: (String, Int) -> Unit,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    onChooseDate: (Int, String) -> Unit,
    onDelete: () -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val poll = state.poll
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state, today, onOpenEvent)
        state.answers.forEach { card -> AnswerSection(poll, card, today, onAnswer, onToggleChoice, onSend, enabled = !state.isBusy) }

        SectionTitle(stringResource(R.string.poll_results))
        state.results.forEach { result ->
            ResultCard(poll, result, today, canChoose = state.canChooseDate && !state.isBusy, onChoose = { choosing = result.index })
        }
        if (state.results.all { it.yesNames.isEmpty() && it.maybeNames.isEmpty() }) Hint(stringResource(R.string.poll_no_answer))
        if (state.table.isNotEmpty() && state.results.any { it.yesNames.isNotEmpty() || it.maybeNames.isNotEmpty() }) {
            AnswersTable(poll, state.table)
        }
        Hint(
            if (state.notAnsweredNames.isEmpty()) {
                stringResource(R.string.poll_everyone_answered)
            } else {
                stringResource(R.string.poll_not_answered, state.notAnsweredNames.joinToString(", "))
            },
        )

        if (state.canManage && state.isOpen) {
            OutlinedButton(onClick = { confirmClose = true }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.poll_close))
            }
        }
        if (state.canManage) {
            TextButton(onClick = { confirmDelete = true }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.poll_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmClose) {
        Confirm(
            title = stringResource(R.string.poll_close_title),
            body = stringResource(R.string.poll_close_body),
            confirm = stringResource(R.string.poll_close_confirm),
            onConfirm = { confirmClose = false; onClose() },
            onDismiss = { confirmClose = false },
        )
    }
    if (confirmDelete) {
        Confirm(
            title = stringResource(R.string.poll_delete_title),
            body = stringResource(R.string.poll_delete_body),
            confirm = stringResource(R.string.poll_delete_confirm),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    val index = choosing
    if (index != null) {
        var title by rememberSaveable { mutableStateOf(poll.question.trim().removeSuffix("?").trim()) }
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.poll_choose_title, optionLabel(poll, index, today).lowercase())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.poll_choose_body))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(PollDetailViewModel.EVENT_TITLE_MAX) },
                        label = { Text(stringResource(R.string.poll_choose_event_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null; onChooseDate(index, title) }) { Text(stringResource(R.string.poll_choose_confirm)) }
            },
            dismissButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(state: PollDetailUiState.Success, today: LocalDate, onOpenEvent: (String) -> Unit) {
    val poll = state.poll
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kindLabel(poll.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(poll.question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Hint(if (state.isMine) stringResource(R.string.poll_by_me) else stringResource(R.string.poll_by, state.authorName))
        val status = when {
            poll.chosen != null -> stringResource(R.string.poll_chosen_date, optionLabel(poll, poll.chosen, today))
            !state.isOpen -> stringResource(R.string.poll_closed)
            poll.closesAt != null -> stringResource(R.string.poll_closes_on, closingDayLabel(lastDayOf(poll.closesAt)))
            else -> null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.titleSmall)
        val eventId = poll.createdEventId
        if (eventId != null) {
            OutlinedButton(onClick = { onOpenEvent(eventId) }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.poll_see_event))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerSection(
    poll: Poll,
    card: AnswerCard,
    today: LocalDate,
    onAnswer: (String, Int, DateAnswer) -> Unit,
    onToggleChoice: (String, Int) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean,
) {
    ClayCard(Modifier.fillMaxWidth()) {
        Text(
            if (card.isMe) stringResource(R.string.poll_my_answer) else stringResource(R.string.poll_answer_for, card.firstName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(8.dp))
        if (poll.kind == Poll.Kind.Date) {
            poll.options.indices.forEach { i ->
                Text(optionLabel(poll, i, today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DateAnswer.entries.forEach { answer ->
                        val selected = card.draft.answers.getOrNull(i) == answer
                        FilterChip(
                            selected = selected,
                            onClick = { onAnswer(card.memberId, i, answer) },
                            label = { Text(answerLabel(answer), style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
            }
        } else {
            poll.options.indices.forEach { i ->
                val selected = i in card.draft.choices
                val toggle = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .let {
                        if (poll.multiple) {
                            it.toggleable(value = selected, role = Role.Checkbox) { onToggleChoice(card.memberId, i) }
                        } else {
                            it.selectable(selected = selected, role = Role.RadioButton) { onToggleChoice(card.memberId, i) }
                        }
                    }
                Row(toggle, verticalAlignment = Alignment.CenterVertically) {
                    if (poll.multiple) Checkbox(checked = selected, onCheckedChange = null) else RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(optionLabel(poll, i, today), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (card.incomplete) {
            Text(
                stringResource(if (poll.kind == Poll.Kind.Date) R.string.poll_answer_incomplete else R.string.poll_answer_none_chosen),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        if (card.isSaved) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.poll_answer_saved), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // A tonal button: the screen keeps at most one filled button (charte Argile).
            FilledTonalButton(onClick = { onSend(card.memberId) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (card.isMe) stringResource(R.string.poll_answer_send) else stringResource(R.string.poll_answer_send_for, card.firstName))
            }
        }
    }
}

@Composable
private fun ResultCard(poll: Poll, result: OptionResult, today: LocalDate, canChoose: Boolean, onChoose: () -> Unit) {
    // Tints say who an event is for (charte Argile): the lead is told in words.
    ClayCard(Modifier.fillMaxWidth()) {
        if (result.isChosen || result.isLeader) {
            Text(
                stringResource(if (result.isChosen) R.string.poll_chosen else R.string.poll_leading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(optionLabel(poll, result.index, today), style = MaterialTheme.typography.titleMedium)
        val counts = if (poll.kind == Poll.Kind.Date) {
            listOf(
                stringResource(R.string.poll_count_yes, result.yesNames.size),
                stringResource(R.string.poll_count_maybe, result.maybeNames.size),
            ).joinToString(" · ")
        } else {
            stringResource(R.string.poll_count_choice, result.yesNames.size)
        }
        Text(counts, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (result.yesNames.isNotEmpty()) {
            Text(
                if (poll.kind == Poll.Kind.Date) stringResource(R.string.poll_yes_names, result.yesNames.joinToString(", ")) else result.yesNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (result.maybeNames.isNotEmpty()) {
            Text(stringResource(R.string.poll_maybe_names, result.maybeNames.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
        }
        if (canChoose) {
            OutlinedButton(onClick = onChoose, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.poll_choose_date))
            }
        }
    }
}

/** « Par foyer »: a column per date, a row per member asked, ✓ ? ✗ or – while not answered. */
@Composable
private fun AnswersTable(poll: Poll, table: List<TableGroup>) {
    val yes = stringResource(R.string.poll_answer_yes)
    val maybe = stringResource(R.string.poll_answer_maybe)
    val no = stringResource(R.string.poll_answer_no)
    ClayCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.poll_by_household), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { }) {
            Spacer(Modifier.weight(1.4f))
            poll.options.indices.forEach { i ->
                Text(shortDateLabel(poll, i), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        table.forEach { group ->
            Text(
                group.household,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            group.rows.forEach { row ->
                // Read aloud as « Camille : sam. 19/12 oui, dim. 20/12 peut-être ».
                val spoken = row.firstName + " : " + poll.options.indices.joinToString(", ") { i ->
                    shortDateLabel(poll, i) + " " + when (row.answers[i]) {
                        DateAnswer.Yes -> yes
                        DateAnswer.Maybe -> maybe
                        DateAnswer.No -> no
                        null -> "–"
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clearAndSetSemantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.firstName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                    row.answers.forEach { answer ->
                        Text(
                            when (answer) {
                                DateAnswer.Yes -> "✓"
                                DateAnswer.Maybe -> "?"
                                DateAnswer.No -> "✗"
                                null -> "–"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when (answer) {
                                DateAnswer.Yes -> MaterialTheme.colorScheme.primary
                                DateAnswer.No -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun answerLabel(answer: DateAnswer): String = stringResource(
    when (answer) {
        DateAnswer.Yes -> R.string.poll_answer_yes
        DateAnswer.Maybe -> R.string.poll_answer_maybe
        DateAnswer.No -> R.string.poll_answer_no
    },
)

@Composable
private fun Confirm(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Fictional data for previews and screenshots. */
internal fun previewPollDetail(kind: Poll.Kind = Poll.Kind.Date): PollDetailUiState.Success {
    val poll = Poll(
        id = "p1",
        kind = kind,
        question = if (kind == Poll.Kind.Date) "Quand fait-on le repas de Noël ?" else "Quel restaurant pour l'anniversaire ?",
        options = if (kind == Poll.Kind.Date) listOf("2026-12-19 12:30", "2026-12-20", "2026-12-26") else listOf("La Pizzeria", "Le Bistrot", "La Crêperie"),
        multiple = false,
        askedIds = listOf("me", "jules", "noe", "ines", "lou"),
        viewerIds = listOf("ines", "me", "jules", "noe", "lou"),
        eventId = null,
        closesAt = Instant.parse("2026-11-30T23:00:00Z"),
        closed = false,
        chosen = null,
        createdEventId = null,
        authorId = "ines",
        authorName = "Inès",
        createdAt = null,
    )
    val date = kind == Poll.Kind.Date
    return PollDetailUiState.Success(
        poll = poll,
        isOpen = true,
        isMine = false,
        authorName = "Inès",
        answers = listOf(
            AnswerCard("me", "Camille", isMe = true, draft = if (date) Draft(listOf(DateAnswer.Yes, DateAnswer.Maybe, DateAnswer.No)) else Draft(choices = setOf(1)), isSaved = true),
            AnswerCard("noe", "Noé", isMe = false, draft = if (date) Draft(listOf(DateAnswer.Yes, null, null)) else Draft(), isSaved = false),
        ),
        results = listOf(
            OptionResult(0, listOf("Camille", "Inès", "Lou"), listOf(), isLeader = true, isChosen = false),
            OptionResult(1, listOf("Inès"), listOf("Camille"), isLeader = false, isChosen = false),
            OptionResult(2, listOf(), listOf("Lou"), isLeader = false, isChosen = false),
        ),
        table = if (date) {
            listOf(
                TableGroup("Foyer Martin", listOf(TableRow("Camille", listOf(DateAnswer.Yes, DateAnswer.Maybe, DateAnswer.No)), TableRow("Jules", listOf(null, null, null)), TableRow("Noé", listOf(null, null, null)))),
                TableGroup("Foyer Petit", listOf(TableRow("Inès", listOf(DateAnswer.Yes, DateAnswer.Yes, DateAnswer.No)), TableRow("Lou", listOf(DateAnswer.Yes, DateAnswer.No, DateAnswer.Maybe)))),
            )
        } else {
            emptyList()
        },
        notAnsweredNames = listOf("Jules", "Noé"),
        canManage = false,
        canChooseDate = false,
    )
}

@FamiliaPreviews
@Composable
private fun PollDetailScreenPreview() {
    FamiliaTheme {
        Surface {
            PollDetailScreen(previewPollDetail(), { _, _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}, today = LocalDate.of(2026, 11, 20))
        }
    }
}
