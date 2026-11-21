package fr.familia.app.feature.gifts.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.gifts.giftAccent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.listSubtitle
import fr.familia.app.feature.gifts.listTitle
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.LocalDate

/** What « Proposer à la famille » can do; the defaults do nothing, for previews and screenshots. */
data class ProposeIdeaActions(
    val onBack: () -> Unit = {},
    val onSelectList: (String) -> Unit = {},
    val onTakeIt: (Boolean) -> Unit = {},
    val onPropose: () -> Unit = {},
    /** A new surprise list for the person, when they have none the idea can go to. */
    val onNewSurprise: (personId: String) -> Unit = {},
)

/** « Proposer à la famille »: which list of the person the idea goes to, hidden from them. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeIdeaScreen(state: ProposeIdeaUiState, actions: ProposeIdeaActions, today: LocalDate = familyToday()) {
    val choosing = state as? ProposeIdeaUiState.Choosing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        bottomBar = {
            if (choosing != null && choosing.lists.isNotEmpty()) {
                StickyActionBar(
                    text = stringResource(R.string.notebook_propose_confirm),
                    onClick = actions.onPropose,
                    enabled = choosing.selectedListId != null,
                    busy = choosing.isSaving,
                )
            }
        },
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.notebook_propose), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = actions.onBack) },
                actions = { HelpButton(ScreenHelp.Notebook) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ProposeIdeaUiState.Loading, is ProposeIdeaUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ProposeIdeaUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.notebook_idea_unavailable_title),
                    body = stringResource(R.string.notebook_idea_unavailable_body),
                )
                is ProposeIdeaUiState.Choosing -> ChoosingContent(state, actions, today)
            }
        }
    }
}

/** The idea, then the list it goes to, in tiles (refonte globale, lot 2). */
@Composable
private fun ChoosingContent(state: ProposeIdeaUiState.Choosing, actions: ProposeIdeaActions, today: LocalDate) {
    val entry = state.entry
    val name = entry.personName
    val accent = giftAccent()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormTile(Icons.Outlined.CardGiftcard, entry.title, accent = accent) {
            entry.price?.let { Text(formatEuros(it), style = MaterialTheme.typography.bodyLarge) }
            FormHint(stringResource(R.string.notebook_propose_body, name))
        }
        if (state.lists.isEmpty()) {
            Text(stringResource(R.string.notebook_propose_no_list, name), style = MaterialTheme.typography.bodyLarge)
            if (state.canPrepareSurprise) {
                OutlinedButton(
                    onClick = { entry.personId?.let(actions.onNewSurprise) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.gifts_prepare_surprise)) }
            }
            return@Column
        }
        FormTile(Icons.AutoMirrored.Outlined.List, stringResource(R.string.notebook_propose_where), accent = accent) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.lists.forEach { list ->
                    ListChoice(list, name, selected = list.id == state.selectedListId, today) { actions.onSelectList(list.id) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = state.takeIt, role = Role.Checkbox, onValueChange = actions.onTakeIt),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.takeIt, onCheckedChange = null)
                Text(stringResource(R.string.notebook_propose_take_it), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
            FormHint(stringResource(R.string.notebook_propose_note_kept))
        }
        if (state.saveFailed) {
            FormHint(
                stringResource(R.string.gift_save_failed),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One list as a choice card: outlined, dark edge and a check when chosen, a radio for TalkBack. */
@Composable
private fun ListChoice(list: GiftList, name: String, selected: Boolean, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) colors.inverseSurface else colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listTitle(list, name, isMine = false), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(listSubtitle(list, name, today), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewProposeIdea(withLists: Boolean = true): ProposeIdeaUiState.Choosing {
    val entry = NotebookEntry(
        id = "e1", source = NotebookSource.Idea, personId = "b", personName = "Inès",
        occasion = NotebookOccasion(GiftOccasion.Christmas, 2026), title = "Une écharpe rouge", url = null,
        price = 25.0, note = "Celle de la vitrine", status = GiftStatus.Idea,
    )
    val lists = if (!withLists) {
        emptyList()
    } else {
        listOf(
            GiftList("wishes-b", GiftListKind.Wishes, "", null, null, "b", emptyList(), "b", "Inès"),
            GiftList("s1", GiftListKind.Surprise, "Noël d'Inès", GiftOccasion.Christmas, LocalDate.of(2026, 12, 25), "b", listOf("a", "c"), "c", "Jules"),
        )
    }
    return ProposeIdeaUiState.Choosing(entry, lists, selectedListId = lists.lastOrNull()?.id, takeIt = false)
}

@FamiliaPreviews
@Composable
private fun ProposeIdeaScreenPreview() {
    FamiliaTheme { Surface { ProposeIdeaScreen(previewProposeIdea(), ProposeIdeaActions(), today = LocalDate.of(2026, 11, 16)) } }
}
