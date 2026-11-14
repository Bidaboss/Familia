package fr.familia.app.feature.events.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import java.time.LocalDate
import java.time.LocalTime

/** An event, read only, and "qui vient" for the member and their children. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onAnswer: (memberId: String, status: RsvpStatus) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_detail_title)) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                actions = {
                    if ((state as? EventDetailUiState.Success)?.canManage == true) {
                        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EventDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                EventDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.event_detail_missing_title),
                    body = stringResource(R.string.event_detail_missing_body),
                    action = {
                        Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_back_to_planning))
                        }
                    },
                )
                EventDetailUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.event_detail_error_title),
                    body = stringResource(R.string.planning_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is EventDetailUiState.Success -> Content(state, onAnswer)
            }
        }
    }
}

@Composable
private fun Content(state: EventDetailUiState.Success, onAnswer: (String, RsvpStatus) -> Unit) {
    val event = state.event
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(event.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        val day = dayTitle(event.date, state.today)
        Text(
            event.time?.let { stringResource(R.string.event_detail_when, day, formatTime(it)) } ?: day,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        event.place?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(R.string.planning_for_by, targetLabel(state.target), state.authorName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        event.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            stringResource(R.string.rsvp_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        state.answerers.forEach { AnswerRow(it, onAnswer) }
        if (state.sendFailed) {
            Text(
                stringResource(R.string.rsvp_send_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Summary(R.string.rsvp_summary_coming, state.coming)
        Summary(R.string.rsvp_summary_maybe, state.maybe)
        Summary(R.string.rsvp_summary_not_coming, state.notComing)
    }
}

/** "Je viens", "Peut-être", "Je ne viens pas" for one person, always visible, 56 dp high. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerRow(answerer: Answerer, onAnswer: (String, RsvpStatus) -> Unit) {
    val name = if (answerer.isMe) stringResource(R.string.rsvp_me) else answerer.member.firstName
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpStatus.entries.forEach { status ->
                val label = stringResource(status.label())
                val selected = answerer.status == status
                val description = stringResource(R.string.rsvp_chip_description, name, label)
                FilterChip(
                    selected = selected,
                    onClick = { onAnswer(answerer.member.id, status) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = description },
                )
            }
        }
    }
}

@Composable
private fun Summary(titleRes: Int, names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(titleRes, names.size), style = MaterialTheme.typography.titleMedium)
        Text(
            if (names.isEmpty()) stringResource(R.string.rsvp_summary_nobody) else names.joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun RsvpStatus.label(): Int = when (this) {
    RsvpStatus.Yes -> R.string.rsvp_yes
    RsvpStatus.Maybe -> R.string.rsvp_maybe
    RsvpStatus.No -> R.string.rsvp_no
}

/** Fictional event for previews and screenshots. */
internal fun previewEventDetail(canManage: Boolean = true): EventDetailUiState.Success {
    fun member(id: String, name: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, "h1", profile, false, emptyList(), null)
    val today = LocalDate.of(2026, 12, 23)
    return EventDetailUiState.Success(
        event = Event(
            "e1", "Repas de Noël", LocalDate.of(2026, 12, 24), LocalTime.of(19, 30), "Chez les parents",
            "Chacun apporte un dessert.", EventTarget.ForFamily, "a", "Camille",
        ),
        today = today,
        target = TargetChoice(EventTarget.ForFamily, null),
        authorName = "Camille",
        canManage = canManage,
        answerers = listOf(
            Answerer(member("a", "Camille"), RsvpStatus.Yes, isMe = true),
            Answerer(member("c", "Noé", MemberProfile.ChildWithoutApp), null, isMe = false),
        ),
        coming = listOf("Camille", "Inès"),
        maybe = listOf("Jules"),
        notComing = emptyList(),
    )
}

@FamiliaPreviews
@Composable
private fun EventDetailScreenPreview() {
    FamiliaTheme { Surface { EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}) } }
}
