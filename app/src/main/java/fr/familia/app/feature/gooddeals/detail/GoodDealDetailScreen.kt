package fr.familia.app.feature.gooddeals.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.linkSiteName
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.gooddeals.categoryLabel
import fr.familia.app.feature.gooddeals.dealUntilLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.helprequests.entraideAccent
import fr.familia.app.feature.help.ScreenHelp
import java.time.Instant
import java.time.LocalDate
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

/**
 * One bon plan or bonne adresse (PRD, récit 45). Stateless. In tiles (plan de refonte du 21/11): the
 * deal, then who is interested or recommends it, « Ça m'intéresse » or « Je recommande aussi » in
 * the black button at the bottom, and « Supprimer » at the very bottom of the page.
 */
@Composable
fun GoodDealDetailScreen(
    state: GoodDealDetailUiState,
    today: LocalDate,
    onToggleInterest: () -> Unit,
    /** Null until the form is there: no « Modifier ». */
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    onToggleRecommend: () -> Unit = {},
) {
    val ready = state as? GoodDealDetailUiState.Ready
    val address = ready?.deal?.kind == GoodDealKind.Address
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = stringResource(if (address) R.string.gooddeal_address_title else R.string.gooddeal_title),
                onBack = onBack,
                actions = { HelpButton(ScreenHelp.HelpRequests) },
            )
        },
        bottomBar = { ready?.let { MainAction(it, onToggleInterest, onToggleRecommend) } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodDealDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                GoodDealDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.LocalOffer,
                    title = stringResource(R.string.gooddeal_missing_title),
                    body = stringResource(R.string.gooddeal_missing_body),
                )
                is GoodDealDetailUiState.Ready -> Content(state, today, onToggleInterest, onToggleRecommend, onEdit, onRemove)
            }
        }
    }
}

/** The black button: « Ça m'intéresse », or « Je recommande aussi » on a bonne adresse; nothing once done. */
@Composable
private fun MainAction(state: GoodDealDetailUiState.Ready, onToggleInterest: () -> Unit, onToggleRecommend: () -> Unit) {
    if (state.deal.kind == GoodDealKind.Address) {
        if (state.canRecommend && !state.iRecommend) StickyActionBar(stringResource(R.string.gooddeal_recommend), onToggleRecommend)
    } else if (!state.iAmInterested) {
        StickyActionBar(stringResource(R.string.gooddeal_interested), onToggleInterest)
    }
}

@Composable
private fun Content(
    state: GoodDealDetailUiState.Ready,
    today: LocalDate,
    onToggleInterest: () -> Unit,
    onToggleRecommend: () -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val deal = state.deal
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val accent = entraideAccent()
    val uriHandler = LocalUriHandler.current
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        val address = deal.kind == GoodDealKind.Address
        FormTile(if (address) Icons.Outlined.Storefront else Icons.Outlined.LocalOffer, deal.title, accent = accent) {
            deal.endsAt?.let {
                Text(dealUntilLabel(it, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            deal.category?.let {
                Text(categoryLabel(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (state.isMine) stringResource(R.string.gooddeal_by_me) else stringResource(R.string.gooddeal_by, deal.authorName),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
            deal.photo?.let { base ->
                StorageImage(
                    path = SinglePhoto.photoPath(base),
                    contentDescription = stringResource(R.string.gooddeal_photo_label),
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
                )
            }
            if (deal.text.isNotBlank()) Text(deal.text, style = MaterialTheme.typography.bodyLarge)
            deal.place?.let {
                Text(stringResource(if (address) R.string.gooddeal_city else R.string.gooddeal_place, it), style = MaterialTheme.typography.bodyLarge, color = muted)
            }
            deal.phone?.let { phone ->
                ClayLink(stringResource(R.string.gooddeal_call, phone), onClick = { runCatching { uriHandler.openUri("tel:" + phone.filter { it.isDigit() || it == '+' }) } })
            }
            deal.link?.let { link ->
                // Opens in the browser, named by its site (récit 45).
                ClayLink(stringResource(R.string.gooddeal_open_link, linkSiteName(link)), onClick = { runCatching { uriHandler.openUri(link) } })
            }
        }
        if (address) RecommendTile(state, accent, onToggleRecommend) else InterestTile(state, accent, onToggleInterest)
        if (state.actionFailed) {
            Text(stringResource(R.string.gooddeal_action_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (state.canManage) {
            if (onEdit != null) FormOutlineButton(stringResource(R.string.gooddeal_edit), onEdit, icon = Icons.Outlined.Edit)
            FormDeleteButton(stringResource(R.string.gooddeal_remove), onClick = { confirmRemove = true })
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.gooddeal_remove_title)) },
            text = { Text(stringResource(R.string.gooddeal_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gooddeal_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Who is interested in a bon plan, children too; « Ça ne m'intéresse plus » once I am. */
@Composable
private fun InterestTile(state: GoodDealDetailUiState.Ready, accent: Color, onToggleInterest: () -> Unit) {
    FormTile(Icons.Outlined.Groups, stringResource(R.string.gooddeal_tile_interested), accent = accent) {
        val names = (if (state.iAmInterested) listOf(stringResource(R.string.gooddeal_me)) else emptyList()) + state.interestedNames
        Text(
            if (names.isEmpty()) stringResource(R.string.gooddeal_interested_nobody) else stringResource(R.string.gooddeal_interested_names, names.joinToString(", ")),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.iAmInterested) FormOutlineButton(stringResource(R.string.gooddeal_not_interested), onToggleInterest)
    }
}

/** « Je recommande aussi » on a bonne adresse: every adult but its author. */
@Composable
private fun RecommendTile(state: GoodDealDetailUiState.Ready, accent: Color, onToggleRecommend: () -> Unit) {
    FormTile(Icons.Outlined.ThumbUp, stringResource(R.string.gooddeal_tile_recommended), accent = accent) {
        val names = (if (state.isMine || state.iRecommend) listOf(stringResource(R.string.gooddeal_me)) else emptyList()) + state.recommendedNames
        Text(
            stringResource(R.string.gooddeal_recommended_by, names.joinToString(", ")),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.canRecommend && state.iRecommend) FormOutlineButton(stringResource(R.string.gooddeal_not_recommend), onToggleRecommend)
    }
}

@FamiliaPreviews
@Composable
private fun GoodDealDetailScreenPreview() {
    val deal = GoodDeal(
        "d1", GoodDealKind.Deal, "Fraises à cueillir soi-même", text = "Le champ ouvre samedi, c'est 4 € le kilo.",
        place = "Ferme des Lilas", link = "https://www.exemple.fr/cueillette", endsAt = Instant.parse("2026-11-28T23:00:00Z"),
        interestedIds = listOf("ines"), authorId = "camille", authorName = "Camille",
    )
    FamiliaTheme {
        Surface {
            GoodDealDetailScreen(
                GoodDealDetailUiState.Ready(deal, isMine = false, canManage = false, iAmInterested = false, interestedNames = listOf("Inès")),
                LocalDate.of(2026, 11, 20), {}, {}, {}, {},
            )
        }
    }
}
