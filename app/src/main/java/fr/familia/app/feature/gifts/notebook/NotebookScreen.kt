package fr.familia.app.feature.gifts.notebook

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpLink
import fr.familia.app.feature.help.ScreenHelp
import java.time.LocalDate

/** What the notebook screen can ask for. */
data class NotebookActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelect: (NotebookOccasion) -> Unit = {},
    val onToggleArchive: () -> Unit = {},
    val onSetStatus: (NotebookEntry, GiftStatus) -> Unit = { _, _ -> },
    val onSetOccasion: (NotebookEntry, NotebookOccasion) -> Unit = { _, _ -> },
    /** Sets the budget of a person at an occasion; null removes it. */
    val onSetBudget: (NotebookPerson, NotebookOccasion, Double?) -> Unit = { _, _, _ -> },
    val onOpenList: (listId: String) -> Unit = {},
    /** A new private idea, at the occasion shown. */
    val onAddIdea: (at: NotebookOccasion?) -> Unit = {},
    val onEditIdea: (entryId: String) -> Unit = {},
    /** « Proposer à la famille »: the idea becomes a surprise item of a list. */
    val onProposeIdea: (entryId: String) -> Unit = {},
    /** « Mon budget » (récit 57): sets the budget of the whole occasion; null removes it. */
    val onSetOccasionBudget: (NotebookOccasion, Double?) -> Unit = { _, _ -> },
    /** « Un achat fait ailleurs »: a gift bought outside the app, which reserves nothing. */
    val onAddPurchase: (NotebookOccasion) -> Unit = {},
)

/** « Mes cadeaux à offrir »: one card per person for the chosen occasion. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(state: NotebookUiState, actions: NotebookActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notebook_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = { HelpButton(ScreenHelp.Notebook) },
            )
        },
        floatingActionButton = {
            if (state is NotebookUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { actions.onAddIdea(state.selected) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.notebook_add_idea)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NotebookUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                NotebookUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gifts_error_title),
                    body = stringResource(R.string.gifts_error_body),
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
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
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
            item(key = "occasions") { OccasionPicker(state, actions.onSelect, actions.onToggleArchive) }
            val budget = state.budget
            val occasion = state.selected
            if (budget != null && occasion != null) {
                item(key = "budget") {
                    OccasionBudgetCard(
                        occasion = occasion,
                        budget = budget,
                        onSetBudget = { amount -> actions.onSetOccasionBudget(occasion, amount) },
                        onAddPurchase = { actions.onAddPurchase(occasion) },
                    )
                }
            }
            items(state.people, key = { it.key }) { person ->
                PersonCard(
                    person = person,
                    onOpen = { openEntryId = it.id },
                    onSetBudget = { amount -> state.selected?.let { actions.onSetBudget(person, it, amount) } },
                )
            }
        }
    }
    state.people.flatMap { it.entries }.firstOrNull { it.id == openEntryId }?.let { entry ->
        StatusSheet(entry, actions, onDismiss = { openEntryId = null })
    }
}

@Composable
private fun OccasionPicker(state: NotebookUiState.Success, onSelect: (NotebookOccasion) -> Unit, onToggleArchive: () -> Unit) {
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
        if (state.archivedCount > 0) {
            item(key = "archive") {
                FilterChip(
                    selected = state.showArchive,
                    onClick = onToggleArchive,
                    label = { Text(stringResource(R.string.notebook_archive, state.archivedCount)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
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

/**
 * « Mon budget » (récit 57): what I mean to spend for the whole occasion, and a soft gauge of what
 * my reservations, my parts and my purchases elsewhere already take. Beyond, it turns orange
 * (the terracotta of the app), with no alert. Nobody else sees it.
 */
@Composable
private fun OccasionBudgetCard(occasion: NotebookOccasion, budget: OccasionBudget, onSetBudget: (Double?) -> Unit, onAddPurchase: () -> Unit) {
    var budgetDialog by rememberSaveable(occasion.occasion, occasion.year) { mutableStateOf(false) }
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val over = MaterialTheme.colorScheme.primary
    ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClayCardTitle(stringResource(R.string.notebook_my_budget, occasionName(occasion)), style = MaterialTheme.typography.titleMedium)
            val amount = budget.amount
            val left = budget.left
            Text(
                when {
                    amount == null || left == null -> stringResource(R.string.notebook_budget_committed, formatEuros(budget.committed))
                    left >= 0 -> stringResource(R.string.notebook_budget_left, formatEuros(left), formatEuros(amount))
                    else -> stringResource(R.string.notebook_budget_over, formatEuros(-left), formatEuros(amount))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (budget.isOver) over else variant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (amount != null) {
                LinearProgressIndicator(
                    progress = { (budget.committed / amount).toFloat().coerceIn(0f, 1f) },
                    color = if (budget.isOver) over else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            Text(
                stringResource(R.string.notebook_budget_private),
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
            )
            ClayLink(
                stringResource(if (amount == null) R.string.notebook_set_my_budget else R.string.notebook_change_budget),
                onClick = { budgetDialog = true },
            )
            ClayLink(stringResource(R.string.notebook_add_purchase), onClick = onAddPurchase)
        }
    }
    if (budgetDialog) {
        BudgetDialog(
            name = occasionName(occasion),
            initial = budget.amount,
            onConfirm = { value -> budgetDialog = false; onSetBudget(value) },
            onDismiss = { budgetDialog = false },
        )
    }
}

/** One person: how many gifts are bought, what was spent, and each gift with where it stands. */
@Composable
private fun PersonCard(person: NotebookPerson, onOpen: (NotebookEntry) -> Unit, onSetBudget: (Double?) -> Unit) {
    val tint = if (person.bought == person.entries.size) ClayTint.Household else ClayTint.Plain
    val variant = FamiliaClay.colors.onTintVariant(tint)
    var budgetDialog by rememberSaveable(person.key) { mutableStateOf(false) }
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint, contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClayCardTitle(person.name, style = MaterialTheme.typography.titleMedium)
            val budget = person.budget
            Text(
                listOf(
                    stringResource(R.string.notebook_bought_of, person.bought, person.entries.size),
                    if (budget != null) {
                        stringResource(R.string.notebook_spent_of_budget, formatEuros(person.spent), formatEuros(budget))
                    } else {
                        stringResource(R.string.notebook_spent, formatEuros(person.spent))
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (budget != null && person.spent > budget) MaterialTheme.colorScheme.error else variant,
            )
            // With a budget, the bar shows what is spent of it; without, how many gifts are bought.
            LinearProgressIndicator(
                progress = {
                    if (budget != null) (person.spent / budget).toFloat().coerceIn(0f, 1f)
                    else person.bought.toFloat() / person.entries.size.coerceAtLeast(1)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            person.entries.forEach { entry -> GiftRow(entry, variant) { onOpen(entry) } }
            ClayLink(
                stringResource(if (budget == null) R.string.notebook_set_budget else R.string.notebook_change_budget),
                onClick = { budgetDialog = true },
            )
        }
    }
    if (budgetDialog) {
        BudgetDialog(
            name = person.name,
            initial = person.budget,
            onConfirm = { amount -> budgetDialog = false; onSetBudget(amount) },
            onDismiss = { budgetDialog = false },
        )
    }
}

/** The budget for one person: an amount, or nothing to remove it. */
@Composable
private fun BudgetDialog(name: String, initial: Double?, onConfirm: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.let { formatEuros(it).replace("€", "").trim() } ?: "") }
    val amount = parseEuros(text)?.takeIf { it > 0 && it <= 100_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_budget_title, name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.notebook_budget_amount)) },
                suffix = { Text("€") },
                isError = text.isNotBlank() && amount == null,
                supportingText = { Text(stringResource(R.string.notebook_budget_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            // An empty field removes the budget.
            TextButton(
                onClick = { if (text.isBlank()) onConfirm(null) else amount?.let(onConfirm) },
                enabled = text.isBlank() || amount != null,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onConfirm(null) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.notebook_remove_budget))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
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
            .clickable(role = Role.Button, onClick = onClick)
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
            onSetOccasion = { occasion -> actions.onSetOccasion(entry, occasion) },
            onOpenList = { listId ->
                onDismiss()
                actions.onOpenList(listId)
            },
            onEdit = {
                onDismiss()
                actions.onEditIdea(entry.id)
            },
            onPropose = {
                onDismiss()
                actions.onProposeIdea(entry.id)
            },
        )
    }
}

/** Where a gift stands, chosen in one touch. Apart for screenshots. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun StatusSheetContent(
    entry: NotebookEntry,
    onSetStatus: (GiftStatus) -> Unit,
    onOpenList: (String) -> Unit,
    onEdit: () -> Unit = {},
    onPropose: () -> Unit = {},
    onSetOccasion: (NotebookOccasion) -> Unit = {},
    thisYear: Int = LocalDate.now().year,
) {
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
        // « Mes envies » (and the ideas of the family) has no occasion: the member says which one the gift is for, the next
        // birthday or the next Christmas; it is revealed the day after (PRD rév. 186). The part of
        // a joint gift follows the choice of its responsible.
        if (isPermanentListId(entry.listId)) {
            Text(
                stringResource(R.string.notebook_idea_occasion),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
            )
            if (entry.source == NotebookSource.Share) {
                Text(
                    stringResource(R.string.notebook_share_occasion, occasionName(entry.occasion)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WISH_OCCASIONS.sortedBy { it.ordinal }.forEach { occasion ->
                        val selected = entry.occasion.occasion == occasion
                        FilterChip(
                            selected = selected,
                            onClick = { onSetOccasion(NotebookOccasion(occasion, thisYear)) },
                            // The year is the server's: the next one after the reservation.
                            label = { Text(if (selected) occasionName(entry.occasion) else occasionLabel(occasion)) },
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
            }
        }
        entry.listId?.let { listId ->
            ClayLink(stringResource(R.string.notebook_open_list), onClick = { onOpenList(listId) })
        }
        if (entry.source == NotebookSource.Idea) {
            ClayLink(stringResource(R.string.notebook_edit_idea), onClick = onEdit)
        }
        if (canPropose(entry)) {
            ClayLink(stringResource(R.string.notebook_propose), onClick = onPropose)
        }
        Spacer(Modifier.height(16.dp))
    }
}

internal const val WISHES_PREFIX = "wishes-"

/** « Mes envies » or the « Idées de la famille » (récit 9 ter): no date, each reservation says its occasion. */
internal fun isPermanentListId(listId: String?): Boolean =
    listId != null && (listId.startsWith(WISHES_PREFIX) || listId.startsWith(IDEAS_PREFIX))

private const val IDEAS_PREFIX = "ideas-"

/** Fictional notebook for previews and screenshots. */
internal fun previewNotebook(): NotebookUiState.Success {
    val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)
    fun entry(id: String, person: String, title: String, price: Double?, status: GiftStatus, source: NotebookSource, personId: String? = person.lowercase()) =
        NotebookEntry(id, source, personId, person, christmas, title, null, price, null, status, listId = if (source == NotebookSource.Idea) null else "l1")
    val people = listOf(
        NotebookPerson("ines", "Inès", personId = "ines", budget = 60.0, entries = listOf(
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
