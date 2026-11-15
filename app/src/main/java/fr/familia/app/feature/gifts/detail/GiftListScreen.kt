package fr.familia.app.feature.gifts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.listSubtitle
import fr.familia.app.feature.gifts.listTitle
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.LocalDate

/** What a gift list screen can ask for. Grouped so the screen stays readable. */
data class GiftListActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEditList: () -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onEditItem: (itemId: String) -> Unit = {},
    val onTake: (itemId: String) -> Unit = {},
    val onOpenJointGift: (itemId: String) -> Unit = {},
    val onRelease: (itemId: String) -> Unit = {},
    val onSetShare: (itemId: String, amount: Double) -> Unit = { _, _ -> },
    val onLeaveShare: (itemId: String) -> Unit = {},
    val onMarkReceived: (itemId: String, memberId: String, received: Boolean) -> Unit = { _, _, _ -> },
)

/** One gift list: its items, and the reservations for everyone but the beneficiary. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftListScreen(state: GiftListUiState, actions: GiftListActions, modifier: Modifier = Modifier) {
    val success = state as? GiftListUiState.Success
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        success?.let { listTitle(it.list, it.beneficiary.firstName, it.isMine) }
                            ?: stringResource(R.string.gift_list_title),
                    )
                },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = {
                    HelpButton(ScreenHelp.GiftList)
                    if (success?.canManage == true && success.list.kind != GiftListKind.Wishes) {
                        TextButton(onClick = actions.onEditList, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (success?.canAddItems == true) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAddItem,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(if (success.list.kind == GiftListKind.Wishes && success.isMine) R.string.gift_add_wish else R.string.gift_add_item)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GiftListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GiftListUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gift_list_missing_title),
                    body = stringResource(R.string.gift_list_missing_body),
                    action = {
                        Button(onClick = actions.onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_back))
                        }
                    },
                )
                GiftListUiState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gifts_error_title),
                    body = stringResource(R.string.planning_error_body),
                    action = {
                        Button(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is GiftListUiState.Success -> Content(state, actions)
            }
        }
    }
}

@Composable
private fun Content(state: GiftListUiState.Success, actions: GiftListActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(state) }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    when {
                        state.keptFromMe -> stringResource(R.string.gift_list_empty_mine)
                        state.list.kind == GiftListKind.Wishes && !state.canAddItems ->
                            stringResource(R.string.gift_wishes_empty_other, state.beneficiary.firstName)
                        else -> stringResource(R.string.gift_list_empty)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(state.items, key = { it.item.id }) { row -> ItemCard(row, state, actions) }
    }
}

@Composable
private fun Header(state: GiftListUiState.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val subtitle = listSubtitle(state.list, state.beneficiary.firstName, state.today)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        val notice = when {
            state.list.revealed -> stringResource(R.string.gift_list_revealed)
            state.keptFromMe && state.list.kind == GiftListKind.Wishes -> stringResource(R.string.gift_list_hidden_wishes)
            state.keptFromMe -> stringResource(R.string.gift_list_hidden_mine)
            state.list.kind == GiftListKind.Surprise -> stringResource(R.string.gift_list_surprise_notice, state.beneficiary.firstName)
            state.list.kind == GiftListKind.Wishes -> stringResource(R.string.gift_list_wishes_notice, state.beneficiary.firstName)
            else -> stringResource(R.string.gift_list_own_notice, state.beneficiary.firstName)
        }
        Text(notice, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions) {
    val item = row.item
    val claim = row.claim
    val tint = if (claim != null) ClayTint.Household else ClayTint.Plain
    val uriHandler = LocalUriHandler.current
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(
                item.price?.let { stringResource(R.string.gift_item_price, formatEuros(it)) },
                if (row.isSurpriseIdea) stringResource(R.string.gift_item_idea_of, item.authorName) else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
            }
            item.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            claim?.let { ClaimStatus(it, item, tint) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                item.url?.let { url ->
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.gift_item_open_link))
                    }
                }
                if (row.canEdit) {
                    OutlinedButton(onClick = { actions.onEditItem(item.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.event_detail_edit))
                    }
                }
                ReservationButtons(row, state, actions)
            }
        }
    }
}

@Composable
private fun ClaimStatus(claim: ClaimView, item: GiftItem, tint: ClayTint) {
    val color = FamiliaClay.colors.onTintVariant(tint)
    when (claim.type) {
        ClaimType.Solo -> Text(
            if (claim.isMine) stringResource(R.string.gift_reserved_by_me) else stringResource(R.string.gift_reserved_by, claim.holderName),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
        ClaimType.Shared -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.gift_joint_by, claim.holderName),
                style = MaterialTheme.typography.titleSmall,
                color = color,
            )
            val total = formatEuros(claim.total)
            Text(
                item.price?.let { stringResource(R.string.gift_joint_total_of, total, formatEuros(it)) }
                    ?: stringResource(R.string.gift_joint_total, total),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReservationButtons(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions) {
    if (!state.canReserve) return
    val id = row.item.id
    val claim = row.claim
    var amountDialog by rememberSaveable(id) { mutableStateOf(false) }
    when {
        claim == null -> {
            Button(onClick = { actions.onTake(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gift_take))
            }
            if (state.canShare) {
                OutlinedButton(onClick = { actions.onOpenJointGift(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gift_open_joint))
                }
            }
        }
        claim.type == ClaimType.Solo && claim.isMine ->
            OutlinedButton(onClick = { actions.onRelease(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gift_release))
            }
        claim.type == ClaimType.Shared -> {
            if (state.canShare) {
                OutlinedButton(onClick = { amountDialog = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(if (claim.myShare == null) R.string.gift_join else R.string.gift_change_share))
                }
                if (claim.myShare != null) {
                    TextButton(onClick = { actions.onLeaveShare(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.gift_leave_share))
                    }
                }
            }
            if (claim.isMine) {
                TextButton(onClick = { actions.onRelease(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gift_cancel_joint))
                }
            }
        }
    }
    if (claim?.type == ClaimType.Shared) Shares(row.item.id, claim, actions)
    if (amountDialog) {
        AmountDialog(
            initial = claim?.myShare?.amount,
            onConfirm = { amount -> amountDialog = false; actions.onSetShare(id, amount) },
            onDismiss = { amountDialog = false },
        )
    }
}

/** The parts of a joint gift. Its responsible ticks the ones they received. */
@Composable
private fun Shares(itemId: String, claim: ClaimView, actions: GiftListActions) {
    if (claim.shares.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.gift_shares_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        claim.shares.forEach { share -> ShareRow(itemId, share, claim.isMine, actions) }
    }
}

@Composable
private fun ShareRow(itemId: String, share: GiftShare, canMark: Boolean, actions: GiftListActions) {
    val label = stringResource(R.string.gift_share_line, share.memberName, formatEuros(share.amount))
    if (canMark) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = share.received, role = Role.Checkbox) {
                    actions.onMarkReceived(itemId, share.memberId, it)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = share.received, onCheckedChange = null)
            Text(stringResource(R.string.gift_share_received_label, label), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Text(
            if (share.received) stringResource(R.string.gift_share_received, label) else label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun AmountDialog(initial: Double?, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.let { formatEuros(it).replace("€", "").trim() } ?: "") }
    val amount = parseEuros(text)?.takeIf { it > 0 && it <= 10_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gift_share_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.gift_share_amount)) },
                isError = text.isNotBlank() && amount == null,
                supportingText = { Text(stringResource(R.string.gift_share_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onConfirm) }, enabled = amount != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Fictional list for previews and screenshots: seen by a viewer, with a joint gift. */
internal fun previewGiftList(keptFromMe: Boolean = false): GiftListUiState.Success {
    val beneficiary = Member("b", "Inès", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    val list = GiftList(
        "l1", GiftListKind.Own, "Noël 2026", GiftOccasion.Christmas, LocalDate.of(2026, 12, 25),
        "b", listOf("a", "b"), "b", "Inès",
    )
    fun item(id: String, title: String, price: Double?, author: String = "Inès", visible: Boolean = true, note: String? = null) =
        GiftItem(id, title, "https://example.com/$id", price, note, if (author == "Inès") "b" else "a", author, visible)
    val rows = listOf(
        ItemRow(item("i1", "Un roman policier", 18.0, note = "Poche, pas grand format"), canEdit = false, isSurpriseIdea = false, claim = null),
        ItemRow(
            item("i2", "Une machine à pain", 120.0),
            canEdit = false,
            isSurpriseIdea = false,
            claim = ClaimView(
                ClaimType.Shared, "Camille", isMine = true,
                shares = listOf(GiftShare("a", "Camille", 40.0, true), GiftShare("c", "Jules", 30.0, false)),
                myShare = GiftShare("a", "Camille", 40.0, true),
            ),
        ),
        ItemRow(item("i3", "Une écharpe rouge", 25.0, author = "Camille", visible = false), canEdit = true, isSurpriseIdea = true,
            claim = ClaimView(ClaimType.Solo, "Jules", isMine = false)),
    )
    return GiftListUiState.Success(
        list = list,
        beneficiary = beneficiary,
        today = LocalDate.of(2026, 11, 15),
        keptFromMe = keptFromMe,
        isMine = keptFromMe,
        canManage = keptFromMe,
        canAddItems = true,
        canReserve = !keptFromMe,
        canShare = !keptFromMe,
        items = if (keptFromMe) rows.take(2).map { it.copy(claim = null) } else rows,
        myName = if (keptFromMe) "Inès" else "Camille",
    )
}

@FamiliaPreviews
@Composable
private fun GiftListScreenPreview() {
    FamiliaTheme { Surface { GiftListScreen(previewGiftList(), GiftListActions()) } }
}

@FamiliaPreviews
@Composable
private fun GiftListScreenMinePreview() {
    FamiliaTheme { Surface { GiftListScreen(previewGiftList(keptFromMe = true), GiftListActions()) } }
}
