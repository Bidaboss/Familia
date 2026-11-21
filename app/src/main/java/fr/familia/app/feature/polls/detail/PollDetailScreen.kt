package fr.familia.app.feature.polls.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.Poll
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.toneColors
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
    today: LocalDate = familyToday(),
    /** « Partager » vers une messagerie (PRD, récit 31); hidden when null. */
    onShare: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.polls_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onShare != null && state is PollDetailUiState.Success) ShareButton(onClick = onShare)
                },
            )
        },
        // « Envoyer ma réponse », the black button at the bottom (maquette lot-1-sondage).
        bottomBar = {
            val success = state as? PollDetailUiState.Success
            val mine = success?.answers?.firstOrNull { it.isMe }
            if (success != null && mine != null && !mine.isSaved) {
                StickyActionBar(stringResource(R.string.poll_answer_send), onClick = { onSend(mine.memberId) }, enabled = !success.isBusy)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PollDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
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
    val accent = pollAccent()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        Header(state, today, accent, onOpenEvent)
        // Mine first, then the children I answer for (maquette lot-1-sondage).
        state.answers.sortedByDescending { it.isMe }.forEach { card ->
            AnswerTile(poll, card, state.results, today, accent, onAnswer, onToggleChoice, onSend, enabled = !state.isBusy)
        }

        FormTile(Icons.Outlined.BarChart, stringResource(R.string.poll_results), accent = accent) {
            state.results.forEach { result ->
                ResultRow(poll, result, today, canChoose = state.canChooseDate && !state.isBusy, onChoose = { choosing = result.index })
            }
            if (state.results.all { it.yesNames.isEmpty() && it.maybeNames.isEmpty() }) FormHint(stringResource(R.string.poll_no_answer))
        }
        if (state.table.isNotEmpty() && state.results.any { it.yesNames.isNotEmpty() || it.maybeNames.isNotEmpty() }) {
            AnswersTable(poll, state.table, accent)
        }

        if (state.canManage && state.isOpen) {
            FormOutlineButton(stringResource(R.string.poll_close), onClick = { confirmClose = true }, enabled = !state.isBusy, icon = Icons.Outlined.Lock)
        }
        if (state.canManage) {
            FormDeleteButton(stringResource(R.string.poll_delete), onClick = { confirmDelete = true }, enabled = !state.isBusy)
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
                TextButton(onClick = { choosing = null; onChooseDate(index, title) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.poll_choose_confirm)) }
            },
            dismissButton = { TextButton(onClick = { choosing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The lilac of « Organiser » (Planning, sondages), for the edge of the tiles and the bars. */
@Composable
internal fun pollAccent(): Color = toneColors(DayTone.Organise).text

/** The kind on a pale pill, the question, who asked and until when, then who has not answered. */
@Composable
private fun Header(state: PollDetailUiState.Success, today: LocalDate, accent: Color, onOpenEvent: (String) -> Unit) {
    val poll = state.poll
    val tone = toneColors(DayTone.Organise)
    FormTile(Icons.Outlined.Poll, poll.question, accent = accent) {
        Text(
            kindLabel(poll.kind),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tone.text,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.pale).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        val status = when {
            poll.chosen != null -> stringResource(R.string.poll_chosen_date, optionLabel(poll, poll.chosen, today))
            !state.isOpen -> stringResource(R.string.poll_closed)
            poll.closesAt != null -> stringResource(R.string.poll_closes_on, closingDayLabel(lastDayOf(poll.closesAt)))
            else -> null
        }
        val by = if (state.isMine) stringResource(R.string.poll_by_me) else stringResource(R.string.poll_by, state.authorName)
        Text(listOfNotNull(by, status).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (state.notAnsweredNames.isEmpty()) {
                stringResource(R.string.poll_everyone_answered)
            } else {
                stringResource(R.string.poll_not_answered, state.notAnsweredNames.joinToString(", "))
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val eventId = poll.createdEventId
        if (eventId != null) FormOutlineButton(stringResource(R.string.poll_see_event), onClick = { onOpenEvent(eventId) }, icon = Icons.Outlined.Event)
    }
}

/**
 * « Ma réponse », or « Réponse de Noé » for a child I answer for (maquette lot-1-sondage). A date:
 * its counts, a bar (dark lilac for yes, lilac for maybe), « En tête », then Oui, Peut-être, Non.
 * A free choice: one pill per answer. Mine is sent by the black button at the bottom, a child's here.
 */
@Composable
private fun AnswerTile(
    poll: Poll,
    card: AnswerCard,
    results: List<OptionResult>,
    today: LocalDate,
    accent: Color,
    onAnswer: (String, Int, DateAnswer) -> Unit,
    onToggleChoice: (String, Int) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean,
) {
    FormTile(
        if (card.isMe) Icons.Outlined.HowToVote else Icons.Outlined.ChildCare,
        if (card.isMe) stringResource(R.string.poll_my_answer) else stringResource(R.string.poll_answer_for, card.firstName),
        accent = accent,
    ) {
        if (poll.kind == Poll.Kind.Date) {
            poll.options.indices.forEach { i ->
                DateAnswerRow(poll, i, results.firstOrNull { it.index == i }, card, today, onAnswer)
            }
        } else if (poll.multiple) {
            poll.options.indices.forEach { i ->
                val selected = i in card.draft.choices
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = selected, role = Role.Checkbox) { onToggleChoice(card.memberId, i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(optionLabel(poll, i, today), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            ChipGroup {
                poll.options.indices.forEach { i ->
                    FormChip(optionLabel(poll, i, today), selected = i in card.draft.choices, onClick = { onToggleChoice(card.memberId, i) })
                }
            }
        }
        if (card.incomplete) {
            FormHint(
                stringResource(if (poll.kind == Poll.Kind.Date) R.string.poll_answer_incomplete else R.string.poll_answer_none_chosen),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (card.isSaved) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.poll_answer_saved), style = MaterialTheme.typography.bodyLarge)
            }
        } else if (!card.isMe) {
            FormOutlineButton(stringResource(R.string.poll_answer_send_for, card.firstName), onClick = { onSend(card.memberId) }, enabled = enabled)
        }
    }
}

/** One date: its label and counts, the bar of yes and maybe, and the three answers side by side. */
@Composable
private fun DateAnswerRow(poll: Poll, index: Int, result: OptionResult?, card: AnswerCard, today: LocalDate, onAnswer: (String, Int, DateAnswer) -> Unit) {
    val tone = toneColors(DayTone.Organise)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(optionLabel(poll, index, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (result != null && (result.isLeader || result.isChosen)) {
                Text(
                    stringResource(if (result.isChosen) R.string.poll_chosen else R.string.poll_leading),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B5320),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF1C2)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (result != null) {
            val yes = result.yesNames.size
            val maybe = result.maybeNames.size
            Text(
                listOf(stringResource(R.string.poll_count_yes, yes), stringResource(R.string.poll_count_maybe, maybe)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tone.text,
            )
            val total = (askedCount(poll) ?: (yes + maybe)).coerceAtLeast(yes + maybe).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics { }) {
                if (yes > 0) Box(Modifier.weight(yes.toFloat()).fillMaxHeight().background(tone.text))
                if (maybe > 0) Box(Modifier.weight(maybe.toFloat()).fillMaxHeight().background(tone.pill))
                val rest = total - yes - maybe
                if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
            }
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateAnswer.entries.forEach { answer ->
                FormChip(
                    answerLabel(answer),
                    selected = card.draft.answers.getOrNull(index) == answer,
                    onClick = { onAnswer(card.memberId, index, answer) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** How many can answer, for the width of the bars: everyone asked, or null when unknown. */
private fun askedCount(poll: Poll): Int? = poll.askedIds.size.takeIf { it > 0 }

/** A line of the results: the option, its counts and names, « Choisir cette date » for the author. */
@Composable
private fun ResultRow(poll: Poll, result: OptionResult, today: LocalDate, canChoose: Boolean, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        if (canChoose) FormOutlineButton(stringResource(R.string.poll_choose_date), onChoose, modifier = Modifier.padding(top = 4.dp))
    }
}

/** « Par foyer »: a column per date, a row per member asked, ✓ ? ✗ or – while not answered. */
@Composable
private fun AnswersTable(poll: Poll, table: List<TableGroup>, accent: Color) {
    val yes = stringResource(R.string.poll_answer_yes)
    val maybe = stringResource(R.string.poll_answer_maybe)
    val no = stringResource(R.string.poll_answer_no)
    FormTile(Icons.Outlined.Groups, stringResource(R.string.poll_by_household), accent = accent) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
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
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) } },
    )
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
