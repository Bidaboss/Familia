package fr.familia.app.feature.joinme

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.form.scrollToFirstError
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.PickGroup
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
    onRecipients: (Set<String>) -> Unit,
    onShortcut: (RecipientShortcut) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.joinme_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.JoinMe) },
            )
        },
        // The main action stays at the bottom of the screen (plan de refonte du 21/11).
        bottomBar = {
            if (state is JoinMeUiState.Editing) {
                StickyActionBar(stringResource(R.string.joinme_send), onSend, enabled = state.canSend, busy = state.sending)
            }
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
                is JoinMeUiState.Editing -> Form(state, onLocate, onSource, onQuery, onSearch, onName, onNote, onRecipients, onShortcut)
            }
        }
    }
}

@Composable
private fun Form(
    state: JoinMeUiState.Editing,
    onLocate: () -> Unit,
    onSource: (PlaceSource) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onName: (String) -> Unit,
    onNote: (String) -> Unit,
    onRecipients: (Set<String>) -> Unit,
    onShortcut: (RecipientShortcut) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    // The screen goes to the first tile with an error (plan de refonte du 21/11).
    val firstError = if (JoinMeError.NameTooLong in state.errors || JoinMeError.NoteTooLong in state.errors) JoinMeTile.Words else null
    val tileTop = scrollToFirstError(scroll, state.errors, firstError)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        FormTile(Icons.Outlined.Place, stringResource(R.string.joinme_where), accent = accent) {
            ChipGroup {
                PlaceSource.entries.forEach { source ->
                    FormChip(
                        label = stringResource(if (source == PlaceSource.Here) R.string.joinme_source_here else R.string.joinme_source_elsewhere),
                        selected = state.source == source,
                        onClick = { onSource(source) },
                        icon = if (source == PlaceSource.Here) Icons.Outlined.MyLocation else Icons.Outlined.Search,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.source == PlaceSource.Here) HereSection(state.lookup, onLocate) else ElsewhereSection(state, onQuery, onSearch)
        }

        FormTile(Icons.Outlined.EditNote, stringResource(R.string.joinme_tile_words), tileTop(JoinMeTile.Words), accent = accent) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.joinme_name)) },
                placeholder = { Text(stringResource(R.string.joinme_name_hint)) },
                isError = JoinMeError.NameTooLong in state.errors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (JoinMeError.NameTooLong in state.errors) FormHint(stringResource(R.string.joinme_name_too_long), isError = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = onNote,
                label = { Text(stringResource(R.string.joinme_note)) },
                placeholder = { Text(stringResource(R.string.joinme_note_hint)) },
                isError = JoinMeError.NoteTooLong in state.errors,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (JoinMeError.NoteTooLong in state.errors) FormHint(stringResource(R.string.joinme_note_too_long), isError = true)
        }

        FormTile(Icons.Outlined.Groups, stringResource(R.string.joinme_who), accent = accent) {
            RecipientsSection(state, onRecipients, onShortcut)
            FormHint(stringResource(R.string.joinme_send_hint))
        }

        if (state.sendFailed) {
            Text(
                stringResource(R.string.joinme_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun HereSection(lookup: PlaceLookup, onLocate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (lookup) {
            PlaceLookup.Searching -> Searching(stringResource(R.string.joinme_locating))
            is PlaceLookup.Found -> {
                PlaceLines(lookup.place)
                FormOutlineButton(stringResource(R.string.joinme_locate_again), onLocate, icon = Icons.Outlined.MyLocation)
            }
            else -> {
                when (lookup) {
                    PlaceLookup.NoPosition -> Problem(stringResource(R.string.joinme_no_position))
                    PlaceLookup.NoPermission -> Problem(stringResource(R.string.joinme_no_permission))
                    else -> Unit
                }
                FormOutlineButton(stringResource(R.string.joinme_locate), onLocate, icon = Icons.Outlined.MyLocation)
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
            FormOutlineButton(stringResource(R.string.joinme_search), onSearch, enabled = state.query.isNotBlank(), icon = Icons.Outlined.Search)
        }
    }
}

@Composable
private fun RecipientsSection(state: JoinMeUiState.Editing, onRecipients: (Set<String>) -> Unit, onShortcut: (RecipientShortcut) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipGroup {
            state.shortcuts.forEach { shortcut ->
                FormChip(
                    label = stringResource(
                        if (shortcut.kind == RecipientShortcut.Kind.MyHousehold) R.string.joinme_who_household else R.string.joinme_who_family,
                    ),
                    selected = state.recipientIds == shortcut.memberIds,
                    onClick = { onShortcut(shortcut) },
                )
            }
        }
        // By household, a box per household ticks all of its members (règle de l'admin, 17/11).
        HouseholdMemberPicker(
            groups = state.groups.map { PickGroup(it.name, it.members) },
            selected = state.recipientIds,
            onChange = onRecipients,
        )
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

private enum class JoinMeTile { Words }
