package fr.familia.app.feature.events.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.place.DirectionsButton
import fr.familia.app.core.place.directionsUri
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
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
    /** « Ajouter à mon agenda » (PRD, récit 30); hidden when null. */
    onAddToCalendar: (() -> Unit)? = null,
    album: @Composable () -> Unit = {},
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
                    HelpButton(ScreenHelp.EventDetail)
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
                is EventDetailUiState.Success -> Content(state, onAnswer, onEdit, onAddToCalendar, album)
            }
        }
    }
}

@Composable
private fun Content(
    state: EventDetailUiState.Success,
    onAnswer: (String, RsvpStatus) -> Unit,
    onEdit: () -> Unit,
    onAddToCalendar: (() -> Unit)?,
    album: @Composable () -> Unit,
) {
    val event = state.event
    var showAnswers by rememberSaveable { mutableStateOf(false) }
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
        event.place?.let { place ->
            Text(place, style = MaterialTheme.typography.bodyLarge)
            // The common place component (récit 33): the route in Google Maps or Waze.
            if (LocalFeatureFlags.current.isOn(Feature.Places)) DirectionsButton(directionsUri(place))
        }
        Text(
            if (event.target is EventTarget.ForFamily) {
                stringResource(R.string.planning_by, state.authorName)
            } else {
                stringResource(R.string.event_detail_for_by, targetLabel(state.target), state.authorName)
            },
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

        // « N viennent » with their avatars; a tap shows every answer (DESIGN-5).
        ComingLine(state, onClick = { showAnswers = true })

        if (onAddToCalendar != null) {
            OutlinedButton(
                onClick = onAddToCalendar,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.calendar_add), style = MaterialTheme.typography.labelLarge)
            }
        }
        // The album of the event (récit 41), when its module is on.
        album()
        if (state.canManage) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().padding(top = if (onAddToCalendar != null) 8.dp else 16.dp).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.event_detail_edit), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (showAnswers) {
        AlertDialog(
            onDismissRequest = { showAnswers = false },
            title = { Text(stringResource(R.string.rsvp_answers_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Summary(R.string.rsvp_summary_coming, state.coming)
                    Summary(R.string.rsvp_summary_maybe, state.maybe)
                    Summary(R.string.rsvp_summary_not_coming, state.notComing)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnswers = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/** Up to five avatars of those who come, then « 3 viennent · 1 peut-être ». */
@Composable
private fun ComingLine(state: EventDetailUiState.Success, onClick: () -> Unit) {
    val coming = state.coming.size
    val maybe = state.maybe.size
    val label = when {
        coming == 0 && maybe == 0 -> stringResource(R.string.rsvp_nobody_yet)
        else -> listOfNotNull(
            pluralStringResource(R.plurals.planning_coming, coming, coming).takeIf { coming > 0 },
            pluralStringResource(R.plurals.planning_maybe, maybe, maybe).takeIf { maybe > 0 },
        ).joinToString(" · ")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.rsvp_answers_open), onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = state.comingMembers.take(5)
        if (shown.isNotEmpty()) {
            Box(Modifier.padding(end = 12.dp)) {
                shown.forEachIndexed { index, member ->
                    MemberAvatar(member, size = 32.dp, modifier = Modifier.padding(start = (index * 22).dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
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
