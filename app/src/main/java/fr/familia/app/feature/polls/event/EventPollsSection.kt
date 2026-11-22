package fr.familia.app.feature.polls.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.form.FormTileHeading
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.polls.list.PollCard
import fr.familia.app.feature.polls.list.PollsUiState
import fr.familia.app.feature.polls.list.PollsViewModel
import java.time.LocalDate

/**
 * The polls of an event on its page (récit 2 quater): open ones first, then their results, and
 * « Lancer un sondage » for an adult. Nothing shows, not even [tile], while loading or when there
 * is none and none can be asked.
 */
@Composable
fun EventPollsSection(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    today: LocalDate,
    onNewPoll: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
    /** The tile around, drawn only once there is something to show: no empty tile while loading. */
    tile: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    val viewModel: PollsViewModel = viewModel(
        key = "eventPolls-$eventId",
        factory = viewModelFactory {
            initializer { PollsViewModel(container?.pollRepository, container?.memberRepository, currentMember.id, eventId = eventId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val success = state as? PollsUiState.Success ?: return
    // In « Voir comme », nothing is asked.
    val canAsk = success.canAsk && LocalViewAs.current.viewed == null
    if (success.isEmpty && !canAsk) return
    tile { EventPollsContent(success, today, canAsk, onNewPoll, onOpenPoll) }
}

@Composable
fun EventPollsContent(
    state: PollsUiState.Success,
    today: LocalDate,
    canAsk: Boolean,
    onNewPoll: () -> Unit,
    onOpenPoll: (pollId: String) -> Unit,
) {
    if (state.isEmpty && !canAsk) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTileHeading(Icons.Outlined.Poll, stringResource(R.string.event_polls_title))
        (state.open + state.closed).forEach { row -> PollCard(row, today, onOpenPoll) }
        if (canAsk) {
            if (state.isEmpty) Text(stringResource(R.string.event_polls_hint), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onNewPoll, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.event_polls_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
