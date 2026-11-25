package fr.familia.app.feature.gifts.surprise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftHistoryItem
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.form.FormHint
import java.text.NumberFormat
import java.util.Locale

/** The pink of the wishes and its edge (fiches §0). */
private val WishPink = Color(0xFFFFF0F6)
private val WishEdge = Color(0xFFF6CADD)
private val Ink = Color(0xFF1E1B18)
private val StarOff = Color(0xFFD9C9A8)
private val StarOn = Color(0xFFD98A2B)

/** What the surprise screen leads to. */
class SurpriseActions(
    val onBack: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRate: (ProposalRow, Int) -> Unit = { _, _ -> },
    val onToggleIdea: (MyIdeaRow) -> Unit = {},
    val onAddIdea: () -> Unit = {},
)

/**
 * A surprise (brief §4, fiche C4): the yellow reminder that the person never sees it, then
 * « Propositions ». The tab « Résultats » comes with the next step. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurpriseScreen(state: SurpriseUiState, actions: SurpriseActions) {
    val ready = state as? SurpriseUiState.Ready
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(ready?.surprise?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = {
                    if (ready?.isCreator == true) {
                        IconButton(onClick = actions.onSettings, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.surprise_settings))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (ready != null) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAddIdea,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.surprise_add_idea), fontWeight = FontWeight.ExtraBold) },
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(24.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                SurpriseUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SurpriseUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.surprise_unavailable_title),
                    body = stringResource(R.string.surprise_unavailable_body),
                )
                is SurpriseUiState.Ready -> Proposals(state, actions)
            }
        }
    }
}

@Composable
private fun Proposals(state: SurpriseUiState.Ready, actions: SurpriseActions) {
    val name = state.beneficiary.firstName
    var ideasOpen by rememberSaveable { mutableStateOf(true) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        // Room under the last tile for the floating button.
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "notice") { SurpriseNotice("🎉 " + stringResource(R.string.surprise_never_sees, name)) }
        if (state.failed) {
            item(key = "failed") {
                FormHint(stringResource(R.string.surprise_write_failed), isError = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        item(key = "wishes-title") {
            Column(Modifier.padding(top = 8.dp)) {
                SectionTitle(stringResource(R.string.surprise_wishes_title, name))
                Text(
                    stringResource(if (state.wishes.isEmpty()) R.string.surprise_wishes_none else R.string.surprise_wishes_body, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.wishes, key = { it.proposal.id }) { row -> ProposalTile(row, null, actions.onRate) }

        item(key = "my-ideas") {
            Foldable(stringResource(R.string.surprise_my_ideas, state.myIdeas.size), ideasOpen) { ideasOpen = !ideasOpen }
        }
        if (ideasOpen) {
            if (state.myIdeas.isEmpty()) {
                item(key = "my-ideas-none") { Hint(stringResource(R.string.surprise_my_ideas_none, name)) }
            }
            items(state.myIdeas, key = { "mine-${it.entry.id}" }) { row -> MyIdea(row, actions.onToggleIdea) }
        }

        item(key = "others-title") { SectionTitle(stringResource(R.string.surprise_ideas_of_others), Modifier.padding(top = 8.dp)) }
        if (state.othersIdeas.isEmpty()) item(key = "others-none") { Hint(stringResource(R.string.surprise_ideas_of_others_none)) }
        items(state.othersIdeas, key = { it.proposal.id }) { row ->
            val by = if (row.isMine) stringResource(R.string.surprise_my_idea) else stringResource(R.string.surprise_idea_of, row.proposal.authorName)
            ProposalTile(row, by, actions.onRate)
        }

        if (state.history.isNotEmpty()) {
            item(key = "history") {
                Foldable(stringResource(R.string.surprise_already_given, name, state.history.size), historyOpen) { historyOpen = !historyOpen }
            }
            if (historyOpen) items(state.history, key = { "given-${it.id}" }) { HistoryRow(it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A section that opens and closes, as « Mes idées (n) » and « Déjà offert à X (n) ». */
@Composable
private fun Foldable(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onToggle).padding(top = 8.dp)
            .semantics { heading(); stateDescription = if (open) "ouvert" else "fermé" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null)
    }
}

/** A wish (pink) or an idea (white, « 💡 Idée de Y »), with its stars (fiche C4). */
@Composable
private fun ProposalTile(row: ProposalRow, by: String?, onRate: (ProposalRow, Int) -> Unit) {
    val wish = row.proposal.kind == ProposalKind.Wish
    Surface(
        color = if (wish) WishPink else Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(20.dp),
        border = if (wish) androidx.compose.foundation.BorderStroke(1.dp, WishEdge) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            by?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Text(row.proposal.title, style = MaterialTheme.typography.titleMedium)
            row.proposal.price?.let { Text(euros(it), style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (wish) R.string.surprise_rate_wish else R.string.surprise_rate_idea), style = MaterialTheme.typography.bodyMedium)
                    Text(ratingLabel(row), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = StarOn)
                }
                Stars(row, onRate)
            }
        }
    }
}

@Composable
private fun ratingLabel(row: ProposalRow): String {
    val p = row.proposal
    return when {
        p.ratingCount == 0 -> stringResource(R.string.surprise_no_rating)
        p.average == null -> pluralStringResource(R.plurals.surprise_few_ratings, p.ratingCount, p.ratingCount)
        else -> stringResource(
            R.string.surprise_average,
            NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 1 }.format(p.average),
            p.ratingCount,
            p.ratingCount,
        )
    }
}

/** Five stars of 48 dp to touch; touching my note again takes it back. */
@Composable
private fun Stars(row: ProposalRow, onRate: (ProposalRow, Int) -> Unit) {
    val mine = row.myStars
    Row {
        (1..5).forEach { n ->
            val on = mine != null && n <= mine
            val label = pluralStringResource(R.plurals.surprise_star, n, n)
            Box(
                Modifier.size(48.dp)
                    .clickable(role = Role.Button) { onRate(row, n) }
                    .semantics { contentDescription = label; selected = mine == n },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = if (on) StarOn else StarOff, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** One of my ideas: 🔒 Privée, or ✓ Ajoutée once the members see it. */
@Composable
private fun MyIdea(row: MyIdeaRow, onToggle: (MyIdeaRow) -> Unit) {
    val action = stringResource(if (row.added) R.string.surprise_idea_remove_action else R.string.surprise_idea_add_action, row.entry.title)
    Surface(color = Color.White, contentColor = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 56.dp)
                .toggleable(value = row.added, role = Role.Switch) { onToggle(row) }
                .semantics { contentDescription = action }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.entry.title, style = MaterialTheme.typography.titleMedium)
                row.entry.price?.let { Text(euros(it), style = MaterialTheme.typography.bodyMedium) }
            }
            Text(
                stringResource(if (row.added) R.string.surprise_idea_added else R.string.surprise_idea_private),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HistoryRow(item: GiftHistoryItem) {
    Surface(color = Color.White, contentColor = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${occasionLabel(item.occasion)} ${item.year}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun euros(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
    maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
}.format(amount)
