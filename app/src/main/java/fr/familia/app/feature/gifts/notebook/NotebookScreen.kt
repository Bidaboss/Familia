package fr.familia.app.feature.gifts.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpLink
import fr.familia.app.feature.help.ScreenHelp

/** What the notebook screen can ask for. */
data class NotebookActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelect: (NotebookOccasion) -> Unit = {},
    val onSetStatus: (NotebookEntry, GiftStatus) -> Unit = { _, _ -> },
    val onOpenList: (listId: String) -> Unit = {},
)

/** « Mes cadeaux à offrir »: one card per person for the chosen occasion. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(state: NotebookUiState, actions: NotebookActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notebook_title)) },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = { HelpButton(ScreenHelp.Notebook) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NotebookUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                NotebookUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gifts_error_title),
                    body = stringResource(R.string.planning_error_body),
                    action = {
                        Button(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is NotebookUiState.Success -> Content(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(state: NotebookUiState.Success, actions: NotebookActions) {
    var openEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "private") {
            Column {
                FlowRow(verticalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(R.string.notebook_private),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    HelpLink(ScreenHelp.Notebook)
                }
                if (state.actionFailed) {
                    Text(
                        stringResource(R.string.gift_action_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        if (state.people.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.notebook_empty_title),
                    body = stringResource(R.string.notebook_empty_body),
                )
            }
        } else {
            item(key = "occasions") { OccasionPicker(state, actions.onSelect) }
            items(state.people, key = { it.key }) { person -> PersonCard(person) { openEntryId = it.id } }
        }
    }
    state.people.flatMap { it.entries }.firstOrNull { it.id == openEntryId }?.let { entry ->
        StatusSheet(entry, actions, onDismiss = { openEntryId = null })
    }
}

@Composable
private fun OccasionPicker(state: NotebookUiState.Success, onSelect: (NotebookOccasion) -> Unit) {
    val label = stringResource(R.string.notebook_occasions)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        items(state.occasions, key = { "${it.occasion}-${it.year}" }) { occasion ->
            FilterChip(
                selected = occasion == state.selected,
                onClick = { onSelect(occasion) },
                label = { Text(occasionName(occasion)) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

/** « Noël 2026 », « Anniversaire 2027 », or « Sans occasion ». */
@Composable
internal fun occasionName(occasion: NotebookOccasion): String {
    val kind = occasion.occasion?.let { occasionLabel(it) }
    return when {
        kind != null && occasion.year != null -> stringResource(R.string.notebook_occasion_year, kind, occasion.year)
        kind != null -> kind
        occasion.year != null -> occasion.year.toString()
        else -> stringResource(R.string.notebook_no_occasion)
    }
}

/** One person: how many gifts are bought, what was spent, and each gift with where it stands. */
@Composable
private fun PersonCard(person: NotebookPerson, onOpen: (NotebookEntry) -> Unit) {
    val tint = if (person.bought == person.entries.size) ClayTint.Household else ClayTint.Plain
    val variant = FamiliaClay.colors.onTintVariant(tint)
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                listOf(
                    stringResource(R.string.notebook_bought_of, person.bought, person.entries.size),
                    stringResource(R.string.notebook_spent, formatEuros(person.spent)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
            )
            LinearProgressIndicator(
                progress = { person.bought.toFloat() / person.entries.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            person.entries.forEach { entry -> GiftRow(entry, variant) { onOpen(entry) } }
        }
    }
}

@Composable
private fun GiftRow(entry: NotebookEntry, variant: Color, onClick: () -> Unit) {
    val details = listOfNotNull(
        statusLabel(entry.status),
        entry.price?.let { formatEuros(it) },
        sourceLabel(entry.source),
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
        Text(details, style = MaterialTheme.typography.bodyMedium, color = variant)
    }
}

@Composable
internal fun statusLabel(status: GiftStatus): String = stringResource(
    when (status) {
        GiftStatus.Idea -> R.string.notebook_status_idea
        GiftStatus.Bought -> R.string.notebook_status_bought
        GiftStatus.Wrapped -> R.string.notebook_status_wrapped
        GiftStatus.Given -> R.string.notebook_status_given
    },
)

@Composable
private fun sourceLabel(source: NotebookSource): String = stringResource(
    when (source) {
        NotebookSource.Idea -> R.string.notebook_source_idea
        NotebookSource.Claim -> R.string.notebook_source_claim
        NotebookSource.Share -> R.string.notebook_source_share
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(entry: NotebookEntry, actions: NotebookActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        StatusSheetContent(
            entry = entry,
            onSetStatus = { status ->
                actions.onSetStatus(entry, status)
                onDismiss()
            },
            onOpenList = { listId ->
                onDismiss()
                actions.onOpenList(listId)
            },
        )
    }
}

/** Where a gift stands, chosen in one touch. Apart for screenshots. */
@Composable
internal fun StatusSheetContent(entry: NotebookEntry, onSetStatus: (GiftStatus) -> Unit, onOpenList: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
        Text(entry.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.notebook_status_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            GiftStatus.entries.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = status == entry.status, role = Role.RadioButton) { onSetStatus(status) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = status == entry.status, onClick = null)
                    Text(statusLabel(status), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        entry.listId?.let { listId ->
            OutlinedButton(onClick = { onOpenList(listId) }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.notebook_open_list))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Fictional notebook for previews and screenshots. */
internal fun previewNotebook(): NotebookUiState.Success {
    val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)
    fun entry(id: String, person: String, title: String, price: Double?, status: GiftStatus, source: NotebookSource, personId: String? = person.lowercase()) =
        NotebookEntry(id, source, personId, person, christmas, title, null, price, null, status, listId = if (source == NotebookSource.Idea) null else "l1")
    val people = listOf(
        NotebookPerson("ines", "Inès", listOf(
            entry("e1", "Inès", "Un roman policier", 18.0, GiftStatus.Bought, NotebookSource.Claim),
            entry("e2", "Inès", "Une machine à pain", 40.0, GiftStatus.Idea, NotebookSource.Share),
        )),
        NotebookPerson("name:mamie jo", "Mamie Jo", listOf(
            entry("e3", "Mamie Jo", "Une écharpe rouge", 25.0, GiftStatus.Wrapped, NotebookSource.Idea, personId = null),
        )),
    )
    return NotebookUiState.Success(
        occasions = listOf(christmas, NotebookOccasion(GiftOccasion.Birthday, 2027), NotebookOccasion(null, null)),
        selected = christmas,
        people = people,
    )
}

@FamiliaPreviews
@Composable
private fun NotebookScreenPreview() {
    FamiliaTheme { Surface { NotebookScreen(previewNotebook(), NotebookActions()) } }
}

@FamiliaPreviews
@Composable
private fun NotebookStatusSheetPreview() {
    FamiliaTheme { Surface { StatusSheetContent(previewNotebook().people.first().entries[1], {}, {}) } }
}
