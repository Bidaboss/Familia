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
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gooddeals.categoryLabel
import fr.familia.app.feature.gooddeals.dealUntilLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.Instant
import java.time.LocalDate

/** One bon plan or bonne adresse (PRD, récit 45). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val address = (state as? GoodDealDetailUiState.Ready)?.deal?.kind == GoodDealKind.Address
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (address) R.string.gooddeal_address_title else R.string.gooddeal_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.HelpRequests) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodDealDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
    val uriHandler = LocalUriHandler.current
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            deal.endsAt?.let {
                Text(dealUntilLabel(it, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            deal.category?.let {
                Text(categoryLabel(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(deal.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                if (state.isMine) stringResource(R.string.gooddeal_by_me) else stringResource(R.string.gooddeal_by, deal.authorName),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
        }
        deal.photo?.let { base ->
            StorageImage(
                path = SinglePhoto.photoPath(base),
                contentDescription = stringResource(R.string.gooddeal_photo_label),
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
            )
        }
        if (deal.text.isNotBlank()) Text(deal.text, style = MaterialTheme.typography.bodyLarge)
        deal.place?.let {
            Text(stringResource(if (deal.kind == GoodDealKind.Address) R.string.gooddeal_city else R.string.gooddeal_place, it), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        deal.phone?.let { phone ->
            ClayLink(stringResource(R.string.gooddeal_call, phone), onClick = { runCatching { uriHandler.openUri("tel:" + phone.filter { it.isDigit() || it == '+' }) } })
        }
        deal.link?.let { link ->
            // Opens in the browser, named by its site (récit 45).
            ClayLink(stringResource(R.string.gooddeal_open_link, linkSiteName(link)), onClick = { runCatching { uriHandler.openUri(link) } })
        }
        if (deal.kind == GoodDealKind.Address) {
            RecommendCard(state, onToggleRecommend)
        } else {
            InterestCard(state, onToggleInterest)
        }
        if (state.actionFailed) {
            Text(stringResource(R.string.gooddeal_action_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (state.canManage) {
            if (onEdit != null) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gooddeal_edit))
                }
            }
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gooddeal_remove), color = MaterialTheme.colorScheme.error)
            }
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

/** « Ça m'intéresse » on a bon plan, children too: the one filled button (DESIGN-11). */
@Composable
private fun InterestCard(state: GoodDealDetailUiState.Ready, onToggleInterest: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), emphasis = !state.isMine) {
        val names = (if (state.iAmInterested) listOf(stringResource(R.string.gooddeal_me)) else emptyList()) + state.interestedNames
        Text(
            if (names.isEmpty()) stringResource(R.string.gooddeal_interested_nobody) else stringResource(R.string.gooddeal_interested_names, names.joinToString(", ")),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.iAmInterested) {
            OutlinedButton(onClick = onToggleInterest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gooddeal_not_interested))
            }
        } else {
            Button(onClick = onToggleInterest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gooddeal_interested))
            }
        }
    }
}

/** « Je recommande aussi » on a bonne adresse: every adult but its author. */
@Composable
private fun RecommendCard(state: GoodDealDetailUiState.Ready, onToggleRecommend: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), emphasis = state.canRecommend && !state.iRecommend) {
        val names = (if (state.isMine || state.iRecommend) listOf(stringResource(R.string.gooddeal_me)) else emptyList()) + state.recommendedNames
        Text(stringResource(R.string.gooddeal_recommended_by, names.joinToString(", ")), style = MaterialTheme.typography.bodyLarge)
        if (state.canRecommend) {
            if (state.iRecommend) {
                OutlinedButton(onClick = onToggleRecommend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gooddeal_not_recommend))
                }
            } else {
                Button(onClick = onToggleRecommend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gooddeal_recommend))
                }
            }
        }
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
