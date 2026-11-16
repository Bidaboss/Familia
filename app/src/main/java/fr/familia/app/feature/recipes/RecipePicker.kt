package fr.familia.app.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * Chooses one recipe of the book, searched by title, for another screen (« Joindre une recette »
 * in Qui apporte quoi, for example). Reads only: nothing is written here. [onPick] gets the
 * recipe; the caller opens it with [RecipeDetailDestination] if it wants.
 */
@Composable
fun RecipePickerDialog(container: AppContainer?, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    val viewModel: RecipesViewModel = viewModel(
        key = "recipe-picker",
        factory = viewModelFactory { initializer { RecipesViewModel(container?.recipeRepository, container?.memberRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecipePickerContent(state, viewModel::search, onPick, onDismiss)
}

@Composable
fun RecipePickerContent(state: RecipesUiState, onSearch: (String) -> Unit, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ready = state as? RecipesUiState.Ready
                OutlinedTextField(
                    value = ready?.query.orEmpty(),
                    onValueChange = onSearch,
                    label = { Text(stringResource(R.string.recipe_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state is RecipesUiState.Failed -> Message(stringResource(R.string.recipe_picker_failed))
                    ready == null -> Unit // Loading: the list arrives in a moment.
                    ready.total == 0 -> Message(stringResource(R.string.recipe_picker_empty))
                    ready.rows.isEmpty() -> Message(stringResource(R.string.recipe_picker_no_match))
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(ready.rows, key = { it.recipe.id }) { row -> PickerRow(row) { onPick(row.recipe) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.recipe_picker_cancel)) }
        },
    )
}

@Composable
private fun PickerRow(row: RecipeRow, onClick: () -> Unit) {
    val label = stringResource(R.string.recipe_picker_pick)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = label, onClick = onClick).padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(row.recipe.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.origin?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
}

@FamiliaPreviews
@Composable
private fun RecipePickerPreview() {
    FamiliaTheme { Surface { RecipePickerContent(RecipesUiState.Ready(emptyList(), "", 0), {}, {}, {}) } }
}
