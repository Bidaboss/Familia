package fr.familia.app.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** One recipe: its ingredients, then its steps, numbered. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    /** The photos of the dish, given by the route (none in previews). */
    photos: @Composable () -> Unit = {},
    /** The comments under the recipe, given by the route. */
    comments: @Composable () -> Unit = {},
    onShare: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (state is RecipeDetailUiState.Ready) {
                        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.recipe_share)) }
                    }
                    HelpButton(ScreenHelp.Recipes)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipeDetailUiState.Loading, RecipeDetailUiState.Removed -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                RecipeDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipe_missing_title),
                    body = stringResource(R.string.recipe_missing_body),
                )
                is RecipeDetailUiState.Ready -> Content(state, onEdit, onRemove, photos, comments)
            }
        }
    }
}

@Composable
private fun Content(
    state: RecipeDetailUiState.Ready,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    photos: @Composable () -> Unit,
    comments: @Composable () -> Unit,
) {
    val recipe = state.recipe
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            state.origin?.let { Text(stringResource(R.string.recipe_from, it), style = MaterialTheme.typography.bodyLarge, color = muted) }
            recipe.servings?.let {
                Text(pluralStringResource(R.plurals.recipe_servings, it, it), style = MaterialTheme.typography.bodyLarge, color = muted)
            }
            if (recipe.tags.isNotEmpty()) Text(recipe.tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipe_photos_title))
            photos()
        }
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.recipe_ingredients))
            recipe.ingredientLines.forEach { line -> Line("•", line) }
        }
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.recipe_steps))
            recipe.stepLines.forEachIndexed { index, line -> Line("${index + 1}.", line) }
        }
        Text(stringResource(R.string.recipe_written_by, recipe.authorName), style = MaterialTheme.typography.bodyMedium, color = muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipe_comments_title))
            comments()
        }
        if (state.canManage) {
            FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.recipe_edit))
            }
            if (state.removeFailed) {
                Text(stringResource(R.string.recipe_remove_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            RemoveButton(recipe.title, onRemove)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Line(mark: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mark, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RemoveButton(title: String, onRemove: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.recipe_remove)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.recipe_remove_title, title)) },
            text = { Text(stringResource(R.string.recipe_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.recipe_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

internal fun previewRecipeDetail(canManage: Boolean = true) =
    RecipeDetailUiState.Ready(previewRecipes().first(), "Mamie Jeanne", canManage)

@FamiliaPreviews
@Composable
private fun RecipeDetailPreview() {
    FamiliaTheme { Surface { RecipeDetailScreen(previewRecipeDetail(), {}, {}, {}) } }
}
