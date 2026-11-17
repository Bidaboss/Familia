package fr.familia.app.feature.polls.list

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Poll
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.polls.closingDayLabel
import fr.familia.app.feature.polls.kindLabel
import fr.familia.app.feature.polls.lastDayOf
import fr.familia.app.feature.polls.optionLabel
import java.time.Instant
import java.time.LocalDate

/** « Sondages », opened from the Planning (récits 2 bis et 2 ter). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollsScreen(
    state: PollsUiState,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.polls_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Polls) },
            )
        },
        floatingActionButton = {
            if ((state as? PollsUiState.Success)?.canAsk == true) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.polls_new)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PollsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PollsUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Poll,
                    title = stringResource(R.string.polls_error_title),
                    body = stringResource(R.string.polls_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                    },
                )
                is PollsUiState.Success -> if (state.isEmpty) {
                    EmptyState(
                        icon = Icons.Outlined.Poll,
                        title = stringResource(R.string.polls_empty_title),
                        body = stringResource(if (state.canAsk) R.string.polls_empty_body else R.string.polls_empty_body_child),
                    )
                } else {
                    PollList(state, today, onOpen)
                }
            }
        }
    }
}

@Composable
private fun PollList(state: PollsUiState.Success, today: LocalDate, onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.open.isNotEmpty()) {
            item(key = "open") { SectionTitle(stringResource(R.string.polls_section_open)) }
            items(state.open, key = { it.poll.id }) { row -> PollCard(row, today, onOpen) }
        }
        if (state.closed.isNotEmpty()) {
            item(key = "closed") { SectionTitle(stringResource(R.string.polls_section_closed)) }
            items(state.closed, key = { it.poll.id }) { row -> PollCard(row, today, onOpen) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

/** One poll of a list: its kind, its question, who asked, what is left to answer or its result. Stateless. */
@Composable
internal fun PollCard(row: PollRow, today: LocalDate, onOpen: (String) -> Unit) {
    val poll = row.poll
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(poll.id) },
        onClickLabel = stringResource(R.string.poll_open_one),
    ) {
        Text(kindLabel(poll.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(poll.question, style = MaterialTheme.typography.titleMedium)
        val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
        Text(
            if (row.isMine) stringResource(R.string.poll_by_me) else stringResource(R.string.poll_by, row.authorName),
            style = MaterialTheme.typography.bodyMedium,
            color = variant,
        )
        when {
            row.meToAnswer -> Text(stringResource(R.string.poll_to_answer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            row.childrenToAnswer.isNotEmpty() -> Text(
                stringResource(R.string.poll_to_answer_for, row.childrenToAnswer.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val status = when {
            poll.chosen != null -> stringResource(R.string.poll_chosen_date, optionLabel(poll, poll.chosen, today))
            !row.isOpen && row.leaders.isNotEmpty() ->
                stringResource(R.string.poll_result, row.leaders.joinToString(" / ") { optionLabel(poll, it, today) })
            !row.isOpen -> stringResource(R.string.poll_closed)
            else -> buildString {
                append(stringResource(R.string.poll_answers_count, row.answeredCount, row.askedCount))
                poll.closesAt?.let { append(" · ").append(stringResource(R.string.poll_closes_on, closingDayLabel(lastDayOf(it)))) }
            }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = variant)
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewPolls(): PollsUiState.Success {
    fun poll(id: String, kind: Poll.Kind, question: String, options: List<String>, author: String, closed: Boolean = false, chosen: Int? = null) = Poll(
        id = id, kind = kind, question = question, options = options, multiple = false,
        askedIds = listOf("me", "b", "c", "d", "e"), viewerIds = listOf(author.lowercase(), "me"), eventId = null,
        closesAt = if (closed) null else Instant.parse("2026-11-30T23:00:00Z"), closed = closed, chosen = chosen,
        createdEventId = null, authorId = author.lowercase(), authorName = author, createdAt = null,
    )
    return PollsUiState.Success(
        open = listOf(
            PollRow(poll("p1", Poll.Kind.Date, "Quand fait-on le repas de Noël ?", listOf("2026-12-19", "2026-12-20"), "Inès"), "Inès", isMine = false, isOpen = true, meToAnswer = true, childrenToAnswer = listOf("Noé"), answeredCount = 2, askedCount = 5, leaders = emptyList()),
            PollRow(poll("p2", Poll.Kind.Choice, "Quel restaurant pour l'anniversaire ?", listOf("La Pizzeria", "Le Bistrot"), "Moi"), "Camille", isMine = true, isOpen = true, meToAnswer = false, childrenToAnswer = emptyList(), answeredCount = 4, askedCount = 5, leaders = emptyList()),
        ),
        closed = listOf(
            PollRow(poll("p3", Poll.Kind.Choice, "Quel film dimanche ?", listOf("Le dessin animé", "Le documentaire"), "Jules", closed = true), "Jules", isMine = false, isOpen = false, meToAnswer = false, childrenToAnswer = emptyList(), answeredCount = 5, askedCount = 5, leaders = listOf(0)),
        ),
        canAsk = true,
    )
}

@FamiliaPreviews
@Composable
private fun PollsScreenPreview() {
    FamiliaTheme { Surface { PollsScreen(previewPolls(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 20)) } }
}
