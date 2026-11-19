package fr.familia.app.feature.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.feature.polls.PollsSummary

/** « + Ajouter » at the bottom of the Planning (récit 75), the same as « Demander » of Entraide. */
@Composable
fun PlanningAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.planning_add)) },
        modifier = modifier,
    )
}

/** What « + Ajouter » adds: an event, or a poll when the module is on. A sheet from the bottom, never a ⋮ menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningAddSheet(onEvent: () -> Unit, onPoll: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // It scrolls, so that it holds at a large font size.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ClaySpacing.screen, end = ClaySpacing.screen, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.planning_add_what),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            AddChoice(Icons.Outlined.Event, R.string.planning_add_event, R.string.planning_add_event_hint) { onDismiss(); onEvent() }
            AddChoice(Icons.Outlined.Poll, R.string.planning_add_poll, R.string.planning_add_poll_hint) { onDismiss(); onPoll() }
        }
    }
}

@Composable
private fun AddChoice(icon: ImageVector, title: Int, hint: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * « Sondages » at the top of the Planning (récit 75), now that the bar has no button for them:
 * how many are open and how many wait for my answer. Shown only while one is open.
 */
@Composable
fun PlanningPollsCard(polls: PollsSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val waiting = if (polls.toVote > 0) pluralStringResource(R.plurals.polls_to_vote, polls.toVote, polls.toVote) else null
    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (waiting != null) stateDescription = waiting },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.planning_polls_open),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        ) {
            Icon(Icons.Outlined.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.polls_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    waiting ?: pluralStringResource(R.plurals.planning_polls_count, polls.open, polls.open),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (polls.toVote > 0) Badge(Modifier.clearAndSetSemantics { }) { Text(polls.toVote.toString()) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}
