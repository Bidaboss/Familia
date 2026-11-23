package fr.familia.app.feature.gifts.detail

import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.photo.CoverBanner
import fr.familia.app.core.photo.CoverThumbnail
import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.isPermanent
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftContribution
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.gifts.listSubtitle
import fr.familia.app.feature.gifts.listTitle
import fr.familia.app.feature.gifts.wishOccasionLabel
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpLink
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.core.share.ShareButton
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
    /** The occasion of a reservation of « Mes envies »; null while the reveal is off (wishesReveal). */
    val onSetClaimOccasion: ((itemId: String, occasion: GiftOccasion) -> Unit)? = null,
    val onSetShare: (itemId: String, amount: Double) -> Unit = { _, _ -> },
    val onLeaveShare: (itemId: String) -> Unit = {},
    val onMarkReceived: (itemId: String, memberId: String, received: Boolean) -> Unit = { _, _, _ -> },
    /** The page of the beneficiary, for their sizes and tastes (récit 7 bis); null while that module is off. */
    val onOpenBeneficiary: ((memberId: String) -> Unit)? = null,
    /** « Je l'ai reçue » on a wish of « Mes envies » (rév. 232); null while the reveal is off. */
    val onMarkWishReceived: ((GiftItem) -> Unit)? = null,
    /** « Partager » « Mes envies » outside the app (récit 31); null while that module is off. */
    val onShareOutside: (() -> Unit)? = null,
    /** « Je participe » on a wish already bought (récit 68), in whole euros. */
    val onContribute: (itemId: String, amount: Int) -> Unit = { _, _ -> },
    val onWithdrawContribution: (itemId: String) -> Unit = {},
    /** « Cadeaux communs » (récit 73): opens the list where the shared wish lives. */
    val onOpenList: (listId: String) -> Unit = {},
)

/** One gift list: its items, and the reservations for everyone but the beneficiary. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftListScreen(state: GiftListUiState, actions: GiftListActions, modifier: Modifier = Modifier) {
    val success = state as? GiftListUiState.Success
    Scaffold(
        modifier = modifier.themeBackground(),
        containerColor = Color.Transparent,
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(
                        success?.let { listTitle(it.list, it.beneficiary.firstName, it.isMine) }
                            ?: stringResource(R.string.gift_list_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = {
                    HelpButton(ScreenHelp.GiftList)
                    if (actions.onShareOutside != null && success?.canSendOutside == true) ShareButton(onClick = actions.onShareOutside)
                    if (success?.canManage == true && !success.list.kind.isPermanent) {
                        // The pencil round of the redone pages (refonte globale, lot 2).
                        IconButton(onClick = actions.onEditList, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.event_detail_edit))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (success?.canAddItems == true) {
                val addLabel = stringResource(
                    when {
                        success.list.kind == GiftListKind.Ideas -> R.string.gift_add_idea
                        success.list.kind == GiftListKind.Wishes && success.isMine -> R.string.gift_add_wish
                        else -> R.string.gift_add_item
                    },
                )
                ExtendedFloatingActionButton(
                    onClick = actions.onAddItem,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = addLabel) },
                    text = { Text(addLabel) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GiftListUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
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
                    body = stringResource(R.string.gifts_error_body),
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
    var openItemId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "header") { Header(state, actions.onOpenBeneficiary) }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                // A card rather than a loose sentence (refonte globale, recette 8); « Ajouter » stays the button below.
                ClayCard(Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            state.keptFromMe -> stringResource(R.string.gift_list_empty_mine)
                            state.list.kind == GiftListKind.Ideas -> stringResource(R.string.gift_ideas_empty)
                            state.list.kind == GiftListKind.Wishes && !state.canAddItems ->
                                stringResource(R.string.gift_wishes_empty_other, state.beneficiary.firstName)
                            else -> stringResource(R.string.gift_list_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.items, key = { it.item.id }) { row ->
            // On my own list, an idea has nothing to reserve: touching it edits it.
            val editsDirectly = state.keptFromMe && row.canEdit
            ItemCard(
                row = row,
                state = state,
                onOpen = { if (editsDirectly) actions.onEditItem(row.item.id) else openItemId = row.item.id },
                openLabel = stringResource(if (editsDirectly) R.string.gift_item_edit_action else R.string.gift_item_details),
                onTake = { actions.onTake(row.item.id) },
            )
        }
        // « Cadeaux communs » (récit 73): reserved, taken part in and edited in the list of their author.
        if (state.sharedWishes.isNotEmpty()) {
            item(key = "shared-title") {
                Text(
                    stringResource(R.string.gift_shared_wishes_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(state.sharedWishes, key = { "shared-${it.listId}-${it.item.id}" }) { shared ->
                SharedWishCard(shared, onOpen = { actions.onOpenList(shared.listId) })
            }
        }
    }
    // The row is looked up again at each change, so that the sheet follows the live list
    // and closes by itself if the idea is removed meanwhile.
    state.items.firstOrNull { it.item.id == openItemId }?.let { row ->
        ItemSheet(row, state, actions, onDismiss = { openItemId = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: GiftListUiState.Success, onOpenBeneficiary: ((String) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val subtitle = listSubtitle(state.list, state.beneficiary.firstName, state.today)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        val notice = when {
            state.list.revealed -> stringResource(R.string.gift_list_revealed)
            state.keptFromReader -> stringResource(R.string.view_as_gift_list_kept)
            state.keptFromMe && state.list.kind == GiftListKind.Wishes -> stringResource(R.string.gift_list_hidden_wishes)
            state.keptFromMe -> stringResource(R.string.gift_list_hidden_mine)
            state.list.kind == GiftListKind.Surprise -> stringResource(R.string.gift_list_surprise_notice, state.beneficiary.firstName)
            state.list.kind == GiftListKind.Ideas -> stringResource(R.string.gift_ideas_notice, state.beneficiary.firstName)
            else -> stringResource(R.string.gift_list_own_notice, state.beneficiary.firstName)
        }
        // One short sentence; the whole explanation is one touch away.
        FlowRow(verticalArrangement = Arrangement.Center) {
            Text(notice, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HelpLink(ScreenHelp.GiftList)
        }
        // Someone else's list: their sizes and tastes are one touch away (récit 7 bis).
        if (onOpenBeneficiary != null && !state.isMine) {
            ClayLink(
                stringResource(R.string.gift_list_beneficiary_card, state.beneficiary.firstName),
                onClick = { onOpenBeneficiary(state.beneficiary.id) },
            )
        }
        ActionMessage(state)
    }
}

/**
 * What did not hold: a reservation someone else made first is said gently, not in red.
 * With [itemId], only what concerns that idea (its sheet covers the list).
 */
@Composable
private fun ActionMessage(state: GiftListUiState.Success, itemId: String? = null) {
    val lost = state.claimLost?.takeIf { itemId == null || it.itemId == itemId }
    val text = when {
        lost?.reason == ClaimLost.Reason.Taken -> stringResource(R.string.gift_claim_taken)
        lost?.reason == ClaimLost.Reason.Removed -> stringResource(R.string.gift_claim_removed)
        lost?.reason == ClaimLost.Reason.AlreadyBought -> stringResource(R.string.gift_claim_already_bought)
        state.actionFailed -> stringResource(R.string.gift_action_failed)
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (lost != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** An idea at a glance: title, price, status, and « Je le prends » when it is free. */
@Composable
private fun ItemCard(row: ItemRow, state: GiftListUiState.Success, onOpen: () -> Unit, openLabel: String, onTake: () -> Unit) {
    val item = row.item
    val claim = row.claim
    val tint = if (claim != null) ClayTint.Household else ClayTint.Plain
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint, onClick = onOpen, onClickLabel = openLabel, contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The photo of its author (récit 44), small: the sheet shows it wide.
                item.photo?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }?.let { photo ->
                    CoverThumbnail(photo, Modifier.padding(end = 12.dp))
                }
                ClayCardTitle(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // Says to the eye what the click label says to TalkBack: the card opens.
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
            ItemDetails(row, tint, state.isMine)
            // The shop link shows on the card itself, as a link, so nobody has to guess it is there.
            item.url?.let { url -> ItemLink(url, prominent = false) }
            claim?.let { ClaimStatus(it, item, tint) }
            row.contributions?.let { AlreadyBoughtStatus(it, item, tint) }
            if (state.canReserve && claim == null && row.contributions == null && !row.sharedWithMe) {
                // Tonal: the screen keeps a single filled action, « Ajouter ».
                FilledTonalButton(onClick = onTake, modifier = Modifier.padding(top = 4.dp).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gift_take))
                }
            }
        }
    }
}

/** A wish shared with the person (récit 73): « Cadeau commun avec X », opened in the list of X. */
@Composable
private fun SharedWishCard(shared: SharedWishRow, onOpen: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.gift_shared_wish_open, shared.ownerName),
        contentPadding = ClaySpacing.card,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClayCardTitle(shared.item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
            val details = listOfNotNull(
                shared.item.price?.let { stringResource(R.string.gift_item_price, formatEuros(it)) },
                stringResource(R.string.gift_item_co_with, shared.ownerName),
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** « Voir sur amazon.fr », with the icon of an outside page: a tonal button in the sheet, a link on the card. */
@Composable
private fun ItemLink(url: String, prominent: Boolean) {
    val uriHandler = LocalUriHandler.current
    val host = linkHost(url)
    val label = if (host != null) stringResource(R.string.gift_item_open_link_on, host) else stringResource(R.string.gift_item_open_link)
    val open = { runCatching { uriHandler.openUri(url) }; Unit }
    if (prominent) {
        FilledTonalButton(onClick = open, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(label)
        }
    } else {
        TextButton(onClick = open, modifier = Modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
        }
    }
}

/** The site of a link without « www. », or null when it cannot be read. */
internal fun linkHost(url: String): String? =
    runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() }

@Composable
private fun ItemDetails(row: ItemRow, tint: ClayTint, forMe: Boolean = false) {
    val details = listOfNotNull(
        row.item.price?.let { stringResource(R.string.gift_item_price, formatEuros(it)) },
        // What a wish is for (récit 9 ter), when it says so.
        row.item.occasion?.let { wishOccasionLabel(it, forMe) },
        if (row.isSurpriseIdea) stringResource(R.string.gift_item_idea_of, row.item.authorName) else null,
        // « Cadeau commun avec… » (récit 73).
        row.sharedWithName?.let { stringResource(R.string.gift_item_co_with, it) },
    ).joinToString(" · ")
    if (details.isNotEmpty()) {
        Text(details, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ItemSheetContent(row, state, actions, onEdit = {
            onDismiss()
            actions.onEditItem(row.item.id)
        })
    }
}

/** Everything about one idea: note, link, edit, joint gift and its parts. Apart for screenshots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemSheetContent(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions, onEdit: () -> Unit) {
    val item = row.item
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val photo = item.photo?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }
        // The photo of its author (récit 44) comes before the picture of the shop page.
        if (photo != null) CoverBanner(photo)
        item.imageUrl?.takeIf { photo == null }?.let { image ->
            // The picture of the shop page of a shared link (récit 8 bis): decorative.
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(MaterialTheme.shapes.medium),
            )
        }
        Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        ItemDetails(row, ClayTint.Plain)
        item.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        row.claim?.let { ClaimStatus(it, item, ClayTint.Plain) }
        row.contributions?.let { AlreadyBoughtStatus(it, item, ClayTint.Plain, detailed = true, seesContributors = state.seesContributors) }
        actions.onSetClaimOccasion?.let { set -> row.claim?.let { claim -> ClaimOccasion(claim) { set(item.id, it) } } }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (row.contributions != null) ContributionButtons(row, state, actions) else ReservationButtons(row, state, actions)
            item.url?.let { url -> ItemLink(url, prominent = true) }
            if (row.canEdit) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_detail_edit))
                }
            }
            val markReceived = actions.onMarkWishReceived
            if (markReceived != null && state.canMarkReceived && item.visibleToBeneficiary) {
                OutlinedButton(onClick = { markReceived(item) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gift_mark_received))
                }
            }
        }
        row.claim?.takeIf { it.type == ClaimType.Shared }?.let { Shares(item.id, it, actions) }
        ActionMessage(state, item.id)
        Spacer(Modifier.height(16.dp))
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

/**
 * « Déjà acheté, participation bienvenue » (récit 68) and its gauge, « 45 € sur 120 € ». In the
 * sheet ([detailed]): my part, and for whoever keeps the person's lists who takes part and how much.
 * Neither in presentation mode, nor in « Voir comme » (no part is read there).
 */
@Composable
private fun AlreadyBoughtStatus(
    view: ContributionView,
    item: GiftItem,
    tint: ClayTint,
    detailed: Boolean = false,
    seesContributors: Boolean = false,
) {
    val color = FamiliaClay.colors.onTintVariant(tint)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gift_already_bought_label), style = MaterialTheme.typography.titleSmall, color = color)
        val total = formatEuros(view.total.toDouble())
        val gauge = contributionGauge(view.total, item.price)
        val spoken = item.price?.let { stringResource(R.string.gift_contributed_of, total, formatEuros(it)) }
            ?: stringResource(R.string.gift_contributed, total)
        if (gauge != null) {
            LinearProgressIndicator(
                progress = { gauge },
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
        Text(spoken, style = MaterialTheme.typography.bodyMedium, color = color)
        if (!detailed) return@Column
        view.mine?.let {
            Text(stringResource(R.string.gift_my_contribution, formatEuros(it.toDouble())), style = MaterialTheme.typography.bodyLarge)
        }
        if (seesContributors) Contributors(view.parts)
    }
}

/** How full the gauge is, from 0 to 1; null without a price to fill. */
internal fun contributionGauge(total: Int, price: Double?): Float? {
    val of = price?.takeIf { it > 0 } ?: return null
    return (total / of).toFloat().coerceIn(0f, 1f)
}

/** Who takes part, never how much: for whoever keeps the person's lists only (récit 93). */
@Composable
private fun Contributors(parts: List<GiftContribution>) {
    Text(
        stringResource(R.string.gift_contributors_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
    if (parts.isEmpty()) {
        Text(stringResource(R.string.gift_contributors_none), style = MaterialTheme.typography.bodyLarge)
    }
    parts.forEach { part ->
        Text(
            part.memberName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/** « Je participe », or change or take back my part: nothing to reserve on a wish already bought. */
@Composable
private fun ContributionButtons(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions) {
    if (!state.canContribute || row.sharedWithMe) return
    val id = row.item.id
    val mine = row.contributions?.mine
    var amountDialog by rememberSaveable(id) { mutableStateOf(false) }
    Button(onClick = { amountDialog = true }, modifier = Modifier.heightIn(min = 56.dp)) {
        Text(stringResource(if (mine == null) R.string.gift_contribute else R.string.gift_contribute_change))
    }
    if (mine != null) {
        TextButton(onClick = { actions.onWithdrawContribution(id) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.gift_contribute_leave))
        }
    }
    if (amountDialog) {
        AmountDialog(
            initial = mine?.toDouble(),
            wholeEuros = true,
            onConfirm = { amount -> amountDialog = false; actions.onContribute(id, amount.toInt()) },
            onDismiss = { amountDialog = false },
        )
    }
}

/**
 * « Mes envies »: which occasion the gift is for, the next birthday or the next Christmas (PRD
 * rév. 186). Its holder chooses; whoever takes part reads it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimOccasion(claim: ClaimView, onSelect: (GiftOccasion) -> Unit) {
    val occasion = claim.occasion ?: return
    if (!claim.isMine) {
        Text(
            stringResource(if (occasion == GiftOccasion.Birthday) R.string.gift_claim_for_birthday else R.string.gift_claim_for_christmas),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    Text(
        stringResource(R.string.notebook_idea_occasion),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
    ChipGroup {
        listOf(GiftOccasion.Christmas, GiftOccasion.Birthday).forEach { choice ->
            FormChip(
                label = occasionLabel(choice),
                selected = choice == occasion,
                onClick = { if (choice != occasion) onSelect(choice) },
            )
        }
    }
    Text(
        stringResource(R.string.gift_claim_occasion_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReservationButtons(row: ItemRow, state: GiftListUiState.Success, actions: GiftListActions) {
    if (!state.canReserve || row.sharedWithMe) return
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
                Button(onClick = { amountDialog = true }, modifier = Modifier.heightIn(min = 56.dp)) {
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
            stringResource(if (claim.isMine) R.string.gift_shares_title else R.string.gift_my_share_title),
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

/** « Je l'ai reçue » cannot be undone, so it is confirmed. It never says anything about a reservation. */
@Composable
internal fun WishReceivedDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gift_mark_received_title)) },
        text = { Text(stringResource(R.string.gift_mark_received_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gift_mark_received_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** [wholeEuros]: a part of a wish already bought (récit 68), « 20 » but not « 12,50 ». */
@Composable
private fun AmountDialog(initial: Double?, onConfirm: (Double) -> Unit, onDismiss: () -> Unit, wholeEuros: Boolean = false) {
    var text by rememberSaveable { mutableStateOf(initial?.let { formatEuros(it).replace("€", "").trim() } ?: "") }
    val amount = parseEuros(text)?.takeIf { it > 0 && it <= 10_000 && (!wholeEuros || it % 1.0 == 0.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (wholeEuros) R.string.gift_contribute_dialog_title else R.string.gift_share_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.gift_share_amount)) },
                isError = text.isNotBlank() && amount == null,
                supportingText = { Text(stringResource(if (wholeEuros) R.string.gift_contribute_hint else R.string.gift_share_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (wholeEuros) KeyboardType.Number else KeyboardType.Decimal),
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

@FamiliaPreviews
@Composable
private fun GiftItemSheetPreview() {
    val state = previewGiftList()
    FamiliaTheme { Surface { ItemSheetContent(state.items[1], state, GiftListActions(), onEdit = {}) } }
}
