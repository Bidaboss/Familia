package fr.familia.app.feature.gifts.surprise

import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import fr.familia.app.R
import fr.familia.app.feature.home.PhotoSegmentedSwitch
import fr.familia.app.feature.gifts.surpriseAccent
import fr.familia.app.feature.gifts.surpriseDark
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
internal val WishPink = Color(0xFFFFF0F6)
internal val WishEdge = Color(0xFFF6CADD)
internal val Ink = Color(0xFF1E1B18)
private val StarOff = Color(0xFFD9C9A8)
internal val StarOn = Color(0xFFD98A2B)

/** The dark brown of the full buttons and of the tab on, the orange of the counts (fiches §0). */
// The dark and the accent follow the photo theme chosen (fiches, § 0).
internal val Brown: Color @Composable get() = surpriseDark()
internal val Accent: Color @Composable get() = surpriseAccent()

/** What the surprise screen leads to. */
class SurpriseActions(
    val onBack: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRate: (ProposalRow, Int) -> Unit = { _, _ -> },
    val onToggleIdea: (MyIdeaRow) -> Unit = {},
    val onAddIdea: () -> Unit = {},
    val onRemove: (ResultRow, Boolean) -> Unit = { _, _ -> },
    val onTakeAlone: (ResultRow) -> Unit = {},
    val onTakeTogether: (ResultRow) -> Unit = {},
    val onRelease: (ResultRow) -> Unit = {},
    val onShare: (ResultRow, Int) -> Unit = { _, _ -> },
    val onValidate: (Boolean) -> Unit = {},
    val onDecline: () -> Unit = {},
    val onOffered: () -> Unit = {},
    val onMute: (Boolean) -> Unit = {},
    val onReinvite: (String) -> Unit = {},
)

/**
 * A surprise (brief §4, fiches C4 to C6): the yellow reminder that the person never sees it, then two
 * tabs, « Propositions » and « Résultats ». Stateless but for the tab shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurpriseScreen(state: SurpriseUiState, actions: SurpriseActions) {
    val ready = state as? SurpriseUiState.Ready
    // Until a tab is chosen: « Résultats » from the day itself (the tile « Surprise à confirmer »
    // opens it, brief §2), « Propositions » before.
    var chosen by rememberSaveable { mutableStateOf<SurpriseTab?>(null) }
    val tab = chosen ?: if (ready?.isDay == true) SurpriseTab.Results else SurpriseTab.Proposals
    val proposalsList = rememberLazyListState()
    val proposalsTop by remember { derivedStateOf { proposalsList.firstVisibleItemIndex == 0 } }
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(ready?.surprise?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = {
                    // ⚙ for everyone: the mute of my reminders (réglage personnel, brief § 9) and, for
                    // the creator, « Modifier la surprise ».
                    if (ready != null && (ready.isCreator || !ready.declined)) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.surprise_settings))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (ready.isCreator) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.surprise_edit_title)) },
                                        onClick = { menu = false; actions.onSettings() },
                                        modifier = Modifier.heightIn(min = 56.dp),
                                    )
                                }
                                if (!ready.declined) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.surprise_mute)) },
                                        trailingIcon = { Switch(checked = ready.muted, onCheckedChange = null) },
                                        onClick = { actions.onMute(!ready.muted) },
                                        modifier = Modifier.heightIn(min = 56.dp),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // « + Ajouter une idée » belongs to « Propositions » (fiche C4).
            // It shrinks to a round « + » of 56 dp once the list scrolls down (fiches, § 0).
            if (ready != null && tab == SurpriseTab.Proposals && !ready.declined) {
                val label = stringResource(R.string.surprise_add_idea)
                ExtendedFloatingActionButton(
                    onClick = actions.onAddIdea,
                    expanded = proposalsTop,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = if (proposalsTop) null else label) },
                    text = { Text(label, fontWeight = FontWeight.ExtraBold) },
                    containerColor = Brown,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp),
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
                is SurpriseUiState.Ready -> {
                    // The orange card of the day itself sits above the tabs, on both (fiche C7).
                    val head: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.isDay && !state.declined) DayCard(state.beneficiary.firstName, actions.onOffered)
                            Tabs(state, tab) { chosen = it }
                        }
                    }
                    when (tab) {
                        SurpriseTab.Proposals -> Proposals(state, actions, head, proposalsList)
                        SurpriseTab.Results -> Results(state, actions, head)
                    }
                }
            }
        }
    }
}

/**
 * « 🎉 C'est le jour J ! » (brief §6, fiche C7): the orange card and its big « 🎁 C'est offert », asked
 * again before. One member is enough: confirmed for all, the tile leaves Cadeaux for everyone.
 */
@Composable
private fun DayCard(name: String, onOffered: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(DayFrom, DayTo)))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎉 " + stringResource(R.string.surprise_day_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Brown)
            Text(stringResource(R.string.surprise_day_question, name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Brown)
            Button(
                onClick = { asking = true },
                shape = RoundedCornerShape(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brown, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) { Text("🎁 " + stringResource(R.string.surprise_offered_action), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
            Text(stringResource(R.string.surprise_day_hint), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Brown)
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.surprise_offered_confirm_title, name)) },
            text = { Text(stringResource(R.string.surprise_offered_confirm_body, name)) },
            confirmButton = {
                TextButton(onClick = { asking = false; onOffered() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.surprise_offered_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The orange of the day itself (fiches §0). */
private val DayFrom = Color(0xFFF7B955)
private val DayTo = Color(0xFFE08A3C)

/** The two tabs of a surprise. */
enum class SurpriseTab { Proposals, Results }

/**
 * « Propositions (n) | Résultats (n) »: the pill switch of the Accueil (.seg, fiche C4), shared with
 * it, in the dark and the accent of the theme.
 */
@Composable
internal fun Tabs(state: SurpriseUiState.Ready, tab: SurpriseTab, onTab: (SurpriseTab) -> Unit) {
    PhotoSegmentedSwitch(
        labels = listOf(stringResource(R.string.surprise_tab_proposals), stringResource(R.string.surprise_tab_results)),
        counts = listOf(state.proposalCount, state.takenCount),
        chosen = tab.ordinal,
        accent = Accent,
        dark = Brown,
        onChoose = { onTab(SurpriseTab.entries[it]) },
    )
}

@Composable
private fun Proposals(state: SurpriseUiState.Ready, actions: SurpriseActions, tabs: @Composable () -> Unit, listState: LazyListState) {
    val name = state.beneficiary.firstName
    var ideasOpen by rememberSaveable { mutableStateOf(true) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        // Room under the last tile for the floating button.
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "notice") { SurpriseNotice("🎉 " + stringResource(R.string.surprise_never_sees, name)) }
        item(key = "tabs") { tabs() }
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
        items(state.wishes, key = { it.proposal.id }) { row -> ProposalTile(row, null, state.surprise.memberIds.size, actions.onRate) }

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
            ProposalTile(row, by, state.surprise.memberIds.size, actions.onRate)
        }

        if (state.history.isNotEmpty()) {
            item(key = "history") {
                Foldable(stringResource(R.string.surprise_already_given, name, state.history.size), historyOpen) { historyOpen = !historyOpen }
            }
            if (historyOpen) items(state.history, key = { "given-${it.id}" }) { HistoryRow(it) }
        }
    }
    // The cream gradient of 110 dp between the list and the floating button (fiches, § 0).
    if (!state.declined) {
        val cream = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(cream.copy(alpha = 0f), cream))),
        )
    }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = modifier.semantics { heading() })
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A section that opens and closes, as « Mes idées (n) » and « Déjà offert à X (n) ». */
@Composable
internal fun Foldable(text: String, open: Boolean, onToggle: () -> Unit) {
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
private fun ProposalTile(row: ProposalRow, by: String?, members: Int, onRate: (ProposalRow, Int) -> Unit) {
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
            Text(stringResource(if (wish) R.string.surprise_rate_wish else R.string.surprise_rate_idea), style = MaterialTheme.typography.bodyMedium)
            Text(ratingLabel(row, members), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = StarOn)
            Stars(row, onRate)
        }
    }
}

@Composable
private fun ratingLabel(row: ProposalRow, members: Int): String {
    val p = row.proposal
    return when {
        p.ratingCount == 0 -> stringResource(R.string.surprise_no_rating)
        p.average == null -> pluralStringResource(R.plurals.surprise_few_ratings, p.ratingCount, p.ratingCount)
        else -> stringResource(
            R.string.surprise_average,
            NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 1 }.format(p.average),
            p.ratingCount,
            members,
        )
    }
}

/** Five stars of 56 dp on their own line (280 dp, fits a 360 dp phone); touching my note again takes it back. */
@Composable
private fun Stars(row: ProposalRow, onRate: (ProposalRow, Int) -> Unit) {
    val mine = row.myStars
    Row {
        (1..5).forEach { n ->
            val on = mine != null && n <= mine
            val label = pluralStringResource(R.plurals.surprise_star, n, n)
            Box(
                Modifier.size(56.dp)
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

internal fun euros(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
    maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
}.format(amount)
