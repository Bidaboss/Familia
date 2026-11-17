package fr.familia.app.feature.genealogy.poster

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.print.printFailedText
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
import java.time.LocalDate
import fr.familia.app.core.ui.LoadingState

/** What the screen can ask, so the route stays short. */
data class TreePosterActions(
    val onTitle: (String) -> Unit,
    val onStart: (PosterStart) -> Unit,
    val onPhotos: (Boolean) -> Unit,
    val onFormat: (PosterFormat) -> Unit,
    val onMake: () -> Unit,
)

/**
 * « Faire l'affiche » (récit 53): the title, where the tree starts, the photos and the size of the
 * sheet, then the single filled button. A line says a photo shop can print it. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreePosterScreen(state: TreePosterUiState, actions: TreePosterActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tree_poster_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TreePosterUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                TreePosterUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.AccountTree,
                    title = stringResource(R.string.tree_poster_empty_title),
                    body = stringResource(R.string.tree_poster_empty_body),
                    action = { ClayLink(stringResource(R.string.tree_poster_empty_back), onClick = onBack) },
                )
                is TreePosterUiState.Choosing -> Choosing(state, actions)
            }
        }
    }
}

@Composable
private fun Choosing(state: TreePosterUiState.Choosing, actions: TreePosterActions) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "intro") {
            Text(stringResource(R.string.tree_poster_intro), style = MaterialTheme.typography.bodyLarge)
        }
        item(key = "title") {
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitle,
                label = { Text(stringResource(R.string.tree_poster_field_title)) },
                supportingText = { Text(stringResource(R.string.tree_poster_year, state.year), color = muted) },
                isError = state.title.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "start") {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                ClayCardTitle(stringResource(R.string.tree_poster_start), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(stringResource(R.string.tree_poster_start_all), state.start == PosterStart.Everyone) { actions.onStart(PosterStart.Everyone) }
                // By household (règle de Kévin, 17/11): one choice, so no box for the household.
                state.starts.forEach { group ->
                    if (group.name.isNotBlank()) {
                        Text(group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                    }
                    group.choices.forEach { choice ->
                        val start = PosterStart.DescendantsOf(choice.id)
                        ChoiceRow(stringResource(R.string.tree_poster_start_from, choice.firstName), state.start == start) { actions.onStart(start) }
                    }
                }
            }
        }
        item(key = "sheet") {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                ClayCardTitle(stringResource(R.string.tree_poster_sheet), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(stringResource(R.string.tree_poster_a4), state.format == PosterFormat.A4) { actions.onFormat(PosterFormat.A4) }
                ChoiceRow(stringResource(R.string.tree_poster_a3), state.format == PosterFormat.A3) { actions.onFormat(PosterFormat.A3) }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = state.withPhotos, role = Role.Checkbox, onValueChange = actions.onPhotos),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = state.withPhotos, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tree_poster_photos), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.tree_poster_photos_children), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                }
            }
        }
        item(key = "make") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.failed) {
                    Text(printFailedText(state.problem, R.string.tree_poster_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = actions.onMake, enabled = state.canMake, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (state.isMaking) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.tree_poster_make))
                    }
                }
                Text(stringResource(R.string.tree_poster_print_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@FamiliaPreviews
@Composable
private fun TreePosterPreview() {
    val tree = GenealogyTree(
        listOf(
            Member("p1", "Camille", null, LocalDate.of(1980, 5, 2), "h1", MemberProfile.Adult, false, emptyList(), null),
            Member("p2", "Noé", null, LocalDate.of(2016, 12, 20), "h1", MemberProfile.ChildWithoutApp, false, listOf("p1"), null),
        ),
        emptyList(),
        listOf(KinLink.parent("p1", "p2")),
    )
    FamiliaTheme {
        Surface {
            TreePosterScreen(
                TreePosterUiState.Choosing(tree, listOf(PosterChoiceGroup("Les Martin", listOf(PosterChoice("p1", "Camille")))), "L'arbre de la famille", 2026),
                TreePosterActions({}, {}, {}, {}, {}),
                onBack = {},
            )
        }
    }
}
