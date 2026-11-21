package fr.familia.app.feature.recipes.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.print.printFailedText
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.recipes.previewRecipes

/** What the screen can ask, so the route stays short. */
data class RecipeBookActions(
    val onToggle: (String) -> Unit,
    val onChooseAll: (Boolean) -> Unit,
    val onTitle: (String) -> Unit,
    val onCover: (String?) -> Unit,
    val onMake: () -> Unit,
)

/**
 * « Faire le livre » (récit 47): the title of the cover, its photo, the recipes to put in, then the
 * single filled button. A line says a photo shop can print it. Stateless.
 */
@Composable
fun RecipeBookScreen(state: RecipeBookUiState, actions: RecipeBookActions, onBack: () -> Unit) {
    val choosing = state as? RecipeBookUiState.Choosing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.recipe_book_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        // The single black button at the bottom (refonte, recette commune n° 7).
        bottomBar = {
            if (choosing != null) {
                StickyActionBar(
                    text = stringResource(R.string.recipe_book_make),
                    onClick = actions.onMake,
                    enabled = choosing.canMake,
                    busy = choosing.isMaking,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipeBookUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                RecipeBookUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipe_book_empty_title),
                    body = stringResource(R.string.recipe_book_empty_body),
                    action = { ClayLink(stringResource(R.string.recipe_book_empty_back), onClick = onBack) },
                )
                is RecipeBookUiState.Choosing -> Choosing(state, actions)
            }
        }
    }
}

/** The book in tiles (refonte lot 4): the cover, then the recipes to put in. */
@Composable
private fun Choosing(state: RecipeBookUiState.Choosing, actions: RecipeBookActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.recipe_book_intro), style = MaterialTheme.typography.bodyLarge)

        FormTile(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.recipe_book_cover)) {
            val label = stringResource(R.string.recipe_book_field_title)
            FormLabel(label)
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitle,
                isError = state.title.isBlank(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            FormHint(stringResource(R.string.recipe_book_year, state.year))
            FormLabel(stringResource(R.string.recipe_book_cover_photo), Modifier.padding(top = 8.dp).semantics { heading() })
            ChipGroup {
                FormChip(stringResource(R.string.recipe_book_cover_none), state.coverRecipeId == null, onClick = { actions.onCover(null) })
                state.coverChoices.forEach { item ->
                    FormChip(
                        stringResource(R.string.recipe_book_cover_of, item.recipe.title),
                        state.coverRecipeId == item.recipe.id,
                        onClick = { actions.onCover(item.recipe.id) },
                    )
                }
            }
        }

        FormTile(
            Icons.Outlined.Restaurant,
            pluralStringResource(R.plurals.recipe_book_chosen, state.chosen.size, state.chosen.size, state.recipes.size),
        ) {
            val all = state.chosen.size == state.recipes.size
            ClayLink(stringResource(if (all) R.string.recipe_book_none else R.string.recipe_book_all), onClick = { actions.onChooseAll(!all) })
            state.recipes.forEach { item ->
                RecipeRow(item, item.recipe.id in state.chosen) { actions.onToggle(item.recipe.id) }
            }
        }

        if (state.failed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(printFailedText(state.problem, R.string.recipe_book_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        FormHint(stringResource(R.string.recipe_book_print_hint))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RecipeRow(item: BookRecipe, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(item.recipe.title, style = MaterialTheme.typography.bodyLarge)
            val about = listOfNotNull(item.recipe.tags.firstOrNull(), item.origin?.let { stringResource(R.string.recipe_from, it) })
            if (about.isNotEmpty()) {
                Text(about.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun RecipeBookPreview() {
    val items = previewRecipes().mapIndexed { i, recipe ->
        BookRecipe(recipe, recipe.origin.ifBlank { null }, if (i == 0) AlbumPhoto("recipes/1", "a", "Camille", 1, null) else null)
    }
    FamiliaTheme {
        Surface {
            RecipeBookScreen(
                RecipeBookUiState.Choosing(items, items.take(2).map { it.recipe.id }.toSet(), "Les recettes de la famille", 2026),
                RecipeBookActions({}, {}, {}, {}, {}),
                onBack = {},
            )
        }
    }
}
