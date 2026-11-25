package fr.familia.app.feature.genealogy.poster

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Title
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.StickyActionBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.print.printFailedText
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
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
@Composable
fun TreePosterScreen(state: TreePosterUiState, actions: TreePosterActions, onBack: () -> Unit) {
    val choosing = state as? TreePosterUiState.Choosing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.tree_poster_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        // The single filled button, fixed at the bottom (refonte, recette commune n° 7).
        bottomBar = {
            if (choosing != null) {
                StickyActionBar(
                    text = stringResource(R.string.tree_poster_make),
                    onClick = actions.onMake,
                    enabled = choosing.title.isNotBlank(),
                    busy = choosing.isMaking,
                )
            }
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

/** The questions in tiles (refonte lot 4): the title, where the tree starts, the sheet. */
@Composable
private fun Choosing(state: TreePosterUiState.Choosing, actions: TreePosterActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tree_poster_intro), style = MaterialTheme.typography.bodyLarge)

        FormTile(Icons.Outlined.Title, stringResource(R.string.tree_poster_field_title)) {
            val label = stringResource(R.string.tree_poster_field_title)
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitle,
                isError = state.title.isBlank(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            FormHint(stringResource(R.string.tree_poster_year, state.year))
        }

        FormTile(Icons.Outlined.AccountTree, stringResource(R.string.tree_poster_start)) {
            val clay = LocalClayColors.current
            PeopleRow(
                choices = listOf(
                    PersonChoice(
                        key = ALL,
                        label = stringResource(R.string.tree_poster_start_all),
                        selected = state.start == PosterStart.Everyone,
                        icon = Icons.Outlined.Groups,
                        tint = clay.family,
                    ),
                ),
                multiple = false,
                onToggle = { actions.onStart(PosterStart.Everyone) },
            )
            // By household (règle de l'admin, 17/11): one choice, so no box for the household.
            state.starts.forEach { group ->
                if (group.name.isNotBlank()) FormLabel(group.name, Modifier.semantics { heading() })
                PeopleRow(
                    choices = group.choices.map { choice ->
                        PersonChoice(
                            key = choice.id,
                            label = choice.firstName,
                            selected = state.start == PosterStart.DescendantsOf(choice.id),
                            memberId = choice.id,
                        )
                    },
                    multiple = false,
                    onToggle = { id -> actions.onStart(PosterStart.DescendantsOf(id)) },
                )
            }
            // The chosen start in words, as the old radio said it.
            val start = state.start as? PosterStart.DescendantsOf
            val name = start?.let { s -> state.starts.flatMap { it.choices }.firstOrNull { it.id == s.personId }?.firstName }
            if (name != null) FormHint(stringResource(R.string.tree_poster_start_from, name))
        }

        FormTile(Icons.Outlined.Description, stringResource(R.string.tree_poster_sheet)) {
            ChipGroup {
                FormChip(stringResource(R.string.tree_poster_a4), state.format == PosterFormat.A4, onClick = { actions.onFormat(PosterFormat.A4) })
                FormChip(stringResource(R.string.tree_poster_a3), state.format == PosterFormat.A3, onClick = { actions.onFormat(PosterFormat.A3) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = state.withPhotos, role = Role.Checkbox, onValueChange = actions.onPhotos),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = state.withPhotos, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tree_poster_photos), style = MaterialTheme.typography.bodyLarge)
                    FormHint(stringResource(R.string.tree_poster_photos_children))
                }
            }
            FormHint(stringResource(R.string.tree_poster_print_hint))
        }

        if (state.failed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(printFailedText(state.problem, R.string.tree_poster_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The key of « Toute la famille » among the rounds; the members have their id. */
private const val ALL = "all"

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
