package fr.familia.app.feature.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.FamiliaPreviews

/** Help page, opened from the Famille tab or a « ? » (spec « tuto et aide », récit 27). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    state: HelpUiState,
    onReplayOnboarding: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyOpen: HelpTopic? = null,
) {
    var openTopic by rememberSaveable { mutableStateOf(initiallyOpen) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "replay") {
                OutlinedButton(
                    onClick = onReplayOnboarding,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Replay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.help_replay_onboarding))
                }
            }
            items(state.topics, key = { it.name }) { topic ->
                TopicRow(topic, open = openTopic == topic, onToggle = { openTopic = if (openTopic == topic) null else topic })
                HorizontalDivider()
            }
            item(key = "contact") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.help_contact_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        state.admins?.let { stringResource(R.string.help_contact_body, it) }
                            ?: stringResource(R.string.help_contact_body_unknown),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: HelpTopic, open: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (open) R.string.help_topic_open else R.string.help_topic_closed)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(topic.titleRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (open) {
            Text(
                stringResource(topic.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun HelpScreenPreview() {
    FamiliaTheme {
        Surface {
            HelpScreen(HelpUiState(helpTopics(isChef = true, isOwner = true), "Camille ou Inès"), {}, {}, initiallyOpen = HelpTopic.AddEvent)
        }
    }
}
