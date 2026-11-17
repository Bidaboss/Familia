package fr.familia.app.feature.recipes.book

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookScreen(state: RecipeBookUiState, actions: RecipeBookActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_book_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipeBookUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                RecipeBookUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipe_book_empty_title),
                    body = stringResource(R.string.recipe_book_empty_body),
                )
                is RecipeBookUiState.Choosing -> Choosing(state, actions)
            }
        }
    }
}

@Composable
private fun Choosing(state: RecipeBookUiState.Choosing, actions: RecipeBookActions) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "intro") {
            Text(stringResource(R.string.recipe_book_intro), style = MaterialTheme.typography.bodyLarge)
        }
        item(key = "cover") {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                ClayCardTitle(stringResource(R.string.recipe_book_cover), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = actions.onTitle,
                    label = { Text(stringResource(R.string.recipe_book_field_title)) },
                    supportingText = { Text(stringResource(R.string.recipe_book_year, state.year), color = muted) },
                    isError = state.title.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.recipe_book_cover_photo), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
                ChoiceRow(stringResource(R.string.recipe_book_cover_none), state.coverRecipeId == null) { actions.onCover(null) }
                state.coverChoices.forEach { item ->
                    ChoiceRow(stringResource(R.string.recipe_book_cover_of, item.recipe.title), state.coverRecipeId == item.recipe.id) {
                        actions.onCover(item.recipe.id)
                    }
                }
            }
        }
        item(key = "recipes-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    pluralStringResource(R.plurals.recipe_book_chosen, state.chosen.size, state.chosen.size, state.recipes.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                val all = state.chosen.size == state.recipes.size
                ClayLink(stringResource(if (all) R.string.recipe_book_none else R.string.recipe_book_all), onClick = { actions.onChooseAll(!all) })
            }
        }
        items(state.recipes, key = { it.recipe.id }) { item ->
            RecipeRow(item, item.recipe.id in state.chosen) { actions.onToggle(item.recipe.id) }
        }
        item(key = "make") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.failed) {
                    Text(stringResource(R.string.recipe_book_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = actions.onMake, enabled = state.canMake, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (state.isMaking) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.recipe_book_make))
                    }
                }
                Text(stringResource(R.string.recipe_book_print_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
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
