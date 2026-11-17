package fr.familia.app.feature.gooddeals.list

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
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.linkSiteName
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.gooddeals.categoryLabel
import fr.familia.app.feature.gooddeals.dealUntilLabel
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The « Bons plans » of Entraide (PRD, récit 45): the bons plans running, then the bonnes adresses. Stateless. */
@Composable
fun GoodDealsScreen(
    state: GoodDealsUiState,
    today: LocalDate,
    /** Null until the form is there: no « Publier ». */
    onPublish: ((GoodDealKind) -> Unit)?,
    /** Null until the sheet of a bon plan is there: the cards do not open. */
    onOpenDeal: ((String) -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPickCategory: (GoodDealCategory?) -> Unit = {},
    onSearch: (String) -> Unit = {},
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        when (state) {
            GoodDealsUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
            GoodDealsUiState.Error -> EmptyState(
                icon = Icons.Outlined.LocalOffer,
                title = stringResource(R.string.gooddeal_error_title),
                body = stringResource(R.string.gooddeal_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is GoodDealsUiState.Success ->
                if (state.deals.isEmpty() && !state.hasAddresses) {
                    EmptyState(
                        icon = Icons.Outlined.LocalOffer,
                        title = stringResource(R.string.gooddeal_empty_title),
                        body = stringResource(R.string.gooddeal_empty_body),
                    )
                } else {
                    // The bottom padding keeps the last card above the « Publier » button.
                    LazyColumn(
                        contentPadding = PaddingValues(start = ClaySpacing.screen, top = 16.dp, end = ClaySpacing.screen, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                    ) {
                        if (state.deals.isNotEmpty()) {
                            item(key = "deals") { SectionTitle(stringResource(R.string.gooddeal_section_deals)) }
                            items(state.deals, key = { it.deal.id }) { row -> DealCard(row, today, onOpenDeal) }
                        }
                        if (state.hasAddresses) {
                            item(key = "addresses") { SectionTitle(stringResource(R.string.gooddeal_section_addresses)) }
                            item(key = "search") { AddressSearch(state.filter, onPickCategory, onSearch) }
                            if (state.addresses.isEmpty()) {
                                item(key = "none") {
                                    Text(stringResource(R.string.gooddeal_address_none), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            items(state.addresses, key = { it.deal.id }) { row -> AddressCard(row, onOpenDeal) }
                        }
                    }
                }
        }
        if (state is GoodDealsUiState.Success && state.canPublish && onPublish != null) {
            ExtendedFloatingActionButton(
                onClick = { choosing = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.gooddeal_publish)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    if (choosing && onPublish != null) {
        // « Publier » offers the two kinds (récit 45).
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.gooddeal_publish_what)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { choosing = false; onPublish(GoodDealKind.Deal) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.gooddeal_kind_deal))
                    }
                    OutlinedButton(onClick = { choosing = false; onPublish(GoodDealKind.Address) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.gooddeal_kind_address))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosing = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

/** Search by name or town, and one category at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddressSearch(filter: AddressFilter, onPickCategory: (GoodDealCategory?) -> Unit, onSearch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.gooddeal_address_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoodDealCategory.entries.forEach { category ->
                FilterChip(
                    selected = filter.category == category,
                    onClick = { onPickCategory(category) },
                    label = { Text(categoryLabel(category)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun AddressCard(row: GoodDealRow, onOpenDeal: ((String) -> Unit)?) {
    val deal = row.deal
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDeal?.let { open -> { open(deal.id) } },
        onClickLabel = onOpenDeal?.let { stringResource(R.string.gooddeal_open_address) },
    ) {
        Text(
            listOfNotNull(deal.category?.let { categoryLabel(it) }, deal.place).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        ClayCardTitle(deal.title, style = MaterialTheme.typography.titleMedium)
        // The author recommends it; each « Je recommande aussi » adds one.
        val count = 1 + deal.recommendedIds.count { it != deal.authorId }
        Text(pluralStringResource(R.plurals.gooddeal_recommended_count, count, count), style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

@Composable
private fun DealCard(row: GoodDealRow, today: LocalDate, onOpenDeal: ((String) -> Unit)?) {
    val deal = row.deal
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDeal?.let { open -> { open(deal.id) } },
        onClickLabel = onOpenDeal?.let { stringResource(R.string.gooddeal_open) },
    ) {
        deal.endsAt?.let {
            Text(dealUntilLabel(it, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        ClayCardTitle(deal.title, style = MaterialTheme.typography.titleMedium)
        val by = if (row.isMine) stringResource(R.string.gooddeal_by_me) else stringResource(R.string.gooddeal_by, deal.authorName)
        Text(
            listOfNotNull(by, deal.place, deal.link?.let(::linkSiteName)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        val count = deal.interestedIds.size
        if (count > 0) {
            Text(
                if (row.iAmInterested) {
                    pluralStringResource(R.plurals.gooddeal_interested_with_me, count, count)
                } else {
                    pluralStringResource(R.plurals.gooddeal_interested_count, count, count)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewGoodDeals(): GoodDealsUiState.Success {
    val now = Instant.parse("2026-11-20T10:00:00Z")
    fun deal(id: String, title: String, days: Long, author: String, place: String? = null, link: String? = null, interested: List<String> = emptyList()) =
        GoodDeal(
            id, GoodDealKind.Deal, title, place = place, link = link, endsAt = now.plus(days, ChronoUnit.DAYS),
            interestedIds = interested, authorId = author.lowercase(), authorName = author,
        )
    return GoodDealsUiState.Success(
        deals = listOf(
            GoodDealRow(deal("d1", "Fraises à cueillir soi-même", 1, "Camille", place = "Ferme des Lilas", interested = listOf("me", "b")), isMine = false, iAmInterested = true),
            GoodDealRow(deal("d2", "Pneus d'hiver à -30 %", 6, "Inès", link = "https://www.exemple.fr/promo"), isMine = false, iAmInterested = false),
            GoodDealRow(deal("d3", "Spectacle de Noël gratuit", 20, "Moi", place = "Salle des fêtes"), isMine = true, iAmInterested = false),
        ),
        canPublish = true,
        addresses = listOf(
            GoodDealRow(
                GoodDeal("a1", GoodDealKind.Address, "Dr Martin, dentiste", place = "Lyon", category = GoodDealCategory.Health, recommendedIds = listOf("b"), authorId = "camille", authorName = "Camille"),
                isMine = false, iAmInterested = false,
            ),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun GoodDealsScreenPreview() {
    FamiliaTheme { Surface { GoodDealsScreen(previewGoodDeals(), LocalDate.of(2026, 11, 20), {}, {}, {}) } }
}
