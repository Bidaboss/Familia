package fr.familia.app.feature.lending.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.lending.lendCategoryLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.of("Europe/Paris"))

/** « Chez Inès depuis le 12/12 », « Demandé par Inès », « Disponible ». */
@Composable
fun lendWhereLabel(item: LendItem, iHaveIt: Boolean): String = when (item.status) {
    LendStatus.Available -> stringResource(R.string.lend_status_available)
    LendStatus.Asked -> stringResource(R.string.lend_status_asked, item.borrowerName.orEmpty())
    LendStatus.Lent -> {
        val since = item.lentAt?.let { SHORT_DAY.format(it) }
        when {
            iHaveIt && since != null -> stringResource(R.string.lend_status_mine_since, since)
            iHaveIt -> stringResource(R.string.lend_status_mine)
            since != null -> stringResource(R.string.lend_status_lent_since, item.borrowerName.orEmpty(), since)
            else -> stringResource(R.string.lend_status_lent, item.borrowerName.orEmpty())
        }
    }
}

/** « On se prête » in Entraide (PRD, récit 51): the things of the family, by name. Stateless. */
@Composable
fun LendItemsScreen(
    state: LendItemsUiState,
    /** Null until the form is there: no « Ajouter un objet ». */
    onAdd: (() -> Unit)?,
    /** Null until the sheet of a thing is there: the cards do not open. */
    onOpen: ((String) -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    onPickCategory: (LendCategory?) -> Unit = {},
    onToggleAvailable: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            LendItemsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LendItemsUiState.Error -> EmptyState(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.lend_error_title),
                body = stringResource(R.string.lend_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is LendItemsUiState.Success ->
                if (!state.hasItems) {
                    EmptyState(
                        icon = Icons.Outlined.Category,
                        title = stringResource(R.string.lend_empty_title),
                        body = stringResource(R.string.lend_empty_body),
                    )
                } else {
                    // The bottom padding keeps the last card above the « Ajouter un objet » button.
                    LazyColumn(
                        contentPadding = PaddingValues(start = ClaySpacing.screen, top = 16.dp, end = ClaySpacing.screen, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                    ) {
                        item(key = "search") { LendSearch(state.filter, onSearch, onPickCategory, onToggleAvailable) }
                        if (state.items.isEmpty()) {
                            item(key = "none") { Text(stringResource(R.string.lend_none), style = MaterialTheme.typography.bodyLarge) }
                        }
                        items(state.items, key = { it.item.id }) { row -> LendCard(row, onOpen) }
                    }
                }
        }
        if (state is LendItemsUiState.Success && state.canAdd && onAdd != null) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.lend_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** Search by name, « Disponible », and one category at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LendSearch(filter: LendFilter, onSearch: (String) -> Unit, onPickCategory: (LendCategory?) -> Unit, onToggleAvailable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.lend_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.availableOnly,
                onClick = onToggleAvailable,
                label = { Text(stringResource(R.string.lend_filter_available)) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
            LendCategory.entries.forEach { category ->
                FilterChip(
                    selected = filter.category == category,
                    onClick = { onPickCategory(category) },
                    label = { Text(lendCategoryLabel(category)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun LendCard(row: LendRow, onOpen: ((String) -> Unit)?) {
    val item = row.item
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen?.let { open -> { open(item.id) } },
        onClickLabel = onOpen?.let { stringResource(R.string.lend_open) },
    ) {
        Text(lendCategoryLabel(item.category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ClayCardTitle(item.name, style = MaterialTheme.typography.titleMedium)
        val owner = if (row.isMine) stringResource(R.string.lend_owner_me) else stringResource(R.string.lend_owner, item.ownerName)
        Text("$owner · ${lendWhereLabel(item, row.iHaveIt)}", style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewLendItems(): LendItemsUiState.Success {
    fun item(id: String, name: String, category: LendCategory, owner: String, status: LendStatus = LendStatus.Available, borrower: String? = null) =
        LendItem(
            id, name, category = category, ownerId = owner.lowercase(), ownerName = owner, status = status,
            borrowerId = borrower?.lowercase(), borrowerName = borrower,
            lentAt = if (status == LendStatus.Lent) Instant.parse("2026-12-12T10:00:00Z") else null,
        )
    return LendItemsUiState.Success(
        items = listOf(
            LendRow(item("i1", "Appareil à raclette", LendCategory.Kitchen, "Camille", LendStatus.Lent, "Inès"), isMine = false, iHaveIt = false),
            LendRow(item("i2", "Lit parapluie", LendCategory.Baby, "Moi"), isMine = true, iHaveIt = false),
            LendRow(item("i3", "Remorque", LendCategory.Diy, "Noé", LendStatus.Asked, "Camille"), isMine = false, iHaveIt = false),
        ),
        canAdd = true,
    )
}

@FamiliaPreviews
@Composable
private fun LendItemsScreenPreview() {
    FamiliaTheme { Surface { LendItemsScreen(previewLendItems(), {}, {}, {}) } }
}
