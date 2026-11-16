package fr.familia.app.feature.joinme

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLines
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** « Rejoignez-moi » (récit 33): where, a name and a note if wanted, then who. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinMeScreen(
    state: JoinMeUiState,
    onLocate: () -> Unit,
    onSource: (PlaceSource) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onName: (String) -> Unit,
    onNote: (String) -> Unit,
    onToggle: (String) -> Unit,
    onShortcut: (RecipientShortcut) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.joinme_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.JoinMe) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                JoinMeUiState.Loading, JoinMeUiState.Sent -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                JoinMeUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.MyLocation,
                    title = stringResource(R.string.joinme_unavailable_title),
                    body = stringResource(R.string.joinme_unavailable_body),
                )
                is JoinMeUiState.Editing -> Form(state, onLocate, onSource, onQuery, onSearch, onName, onNote, onToggle, onShortcut, onSend)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: JoinMeUiState.Editing,
    onLocate: () -> Unit,
    onSource: (PlaceSource) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onName: (String) -> Unit,
    onNote: (String) -> Unit,
    onToggle: (String) -> Unit,
    onShortcut: (RecipientShortcut) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(stringResource(R.string.joinme_where))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceSource.entries.forEach { source ->
                val selected = state.source == source
                FilterChip(
                    selected = selected,
                    onClick = { onSource(source) },
                    label = {
                        Text(
                            stringResource(if (source == PlaceSource.Here) R.string.joinme_source_here else R.string.joinme_source_elsewhere),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        if (state.source == PlaceSource.Here) HereSection(state.lookup, onLocate) else ElsewhereSection(state, onQuery, onSearch)

        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.joinme_name)) },
            placeholder = { Text(stringResource(R.string.joinme_name_hint)) },
            isError = JoinMeError.NameTooLong in state.errors,
            supportingText = if (JoinMeError.NameTooLong in state.errors) ({ Text(stringResource(R.string.joinme_name_too_long)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = onNote,
            label = { Text(stringResource(R.string.joinme_note)) },
            placeholder = { Text(stringResource(R.string.joinme_note_hint)) },
            isError = JoinMeError.NoteTooLong in state.errors,
            supportingText = if (JoinMeError.NoteTooLong in state.errors) ({ Text(stringResource(R.string.joinme_note_too_long)) }) else null,
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        RecipientsSection(state, onToggle, onShortcut)

        if (state.sendFailed) {
            Text(stringResource(R.string.joinme_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.sending) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.joinme_send))
            }
        }
        Hint(stringResource(R.string.joinme_send_hint))
    }
}

@Composable
private fun HereSection(lookup: PlaceLookup, onLocate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint(stringResource(R.string.joinme_here_hint))
        when (lookup) {
            PlaceLookup.Searching -> Searching(stringResource(R.string.joinme_locating))
            is PlaceLookup.Found -> {
                PlaceLines(lookup.place)
                TextButton(onClick = onLocate, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.joinme_locate_again)) }
            }
            else -> {
                when (lookup) {
                    PlaceLookup.NoPosition -> Problem(stringResource(R.string.joinme_no_position))
                    PlaceLookup.NoPermission -> Problem(stringResource(R.string.joinme_no_permission))
                    else -> Unit
                }
                OutlinedButton(onClick = onLocate, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.joinme_locate))
                }
            }
        }
    }
}

@Composable
private fun ElsewhereSection(state: JoinMeUiState.Editing, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.joinme_query)) },
            placeholder = { Text(stringResource(R.string.joinme_query_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (val lookup = state.lookup) {
            PlaceLookup.Searching -> Searching(stringResource(R.string.joinme_searching))
            is PlaceLookup.Found -> PlaceLines(lookup.place)
            PlaceLookup.NotFound -> Problem(stringResource(R.string.joinme_not_found))
            else -> Unit
        }
        if (state.lookup !is PlaceLookup.Found && state.lookup != PlaceLookup.Searching) {
            OutlinedButton(onClick = onSearch, enabled = state.query.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.joinme_search))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipientsSection(state: JoinMeUiState.Editing, onToggle: (String) -> Unit, onShortcut: (RecipientShortcut) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.joinme_who))
        Hint(stringResource(R.string.joinme_who_hint))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            state.shortcuts.forEach { shortcut ->
                val selected = state.recipientIds == shortcut.memberIds
                FilterChip(
                    selected = selected,
                    onClick = { onShortcut(shortcut) },
                    label = {
                        Text(
                            stringResource(
                                if (shortcut.kind == RecipientShortcut.Kind.MyHousehold) R.string.joinme_who_household else R.string.joinme_who_family,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        state.groups.forEach { group ->
            Text(
                group.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            group.members.forEach { member ->
                val checked = member.id in state.recipientIds
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Checkbox) { onToggle(member.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(16.dp))
                    Text(member.firstName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Searching(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Problem(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A form with a place found and two recipients ticked, for the previews and the screenshot tests. */
internal fun previewJoinMeForm(): JoinMeUiState.Editing {
    fun member(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, household, profile, false, emptyList(), null)
    val mine = listOf(member("alex", "Alex", "h1"), member("lou", "Lou", "h1", MemberProfile.ChildWithApp))
    val others = listOf(member("camille", "Camille", "h2"), member("noe", "Noé", "h2"))
    val groups = listOf(RecipientGroup("Foyer Martin", mine), RecipientGroup("Foyer Petit", others))
    return JoinMeUiState.Editing(
        lookup = PlaceLookup.Found(Place(46.4915, -1.7931, address = "1 promenade de la Mer, 85100 Les Sables")),
        name = "Plage des Sables",
        note = "On est près du poste de secours",
        recipientIds = setOf("alex", "lou"),
        groups = groups,
        shortcuts = listOf(
            RecipientShortcut(RecipientShortcut.Kind.MyHousehold, setOf("alex", "lou")),
            RecipientShortcut(RecipientShortcut.Kind.Family, setOf("alex", "lou", "camille", "noe")),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun JoinMeScreenPreview() {
    FamiliaTheme {
        JoinMeScreen(previewJoinMeForm(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

