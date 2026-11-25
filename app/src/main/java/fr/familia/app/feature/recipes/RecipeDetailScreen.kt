package fr.familia.app.feature.recipes

import fr.familia.app.core.ui.form.FormTile
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Article
import fr.familia.app.core.theme.LocalClayColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import fr.familia.app.core.theme.themeBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.ui.ClayCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
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
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/**
 * One recipe (R4, R5): its card (title, « De … », category and tags, story), the photos of the
 * dish, its ingredients, then its steps, numbered. Only its author changes or removes it
 * (DESIGN-173). Stateless.
 */
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
    /** The switch recipeBook (récit 47): the little story. */
    bookOn: Boolean = false,
) {
    Scaffold(
        // The background of the theme, as on the redone screens (refonte lot 4).
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.recipe_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Recipes) },
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
                is RecipeDetailUiState.Ready -> Content(state, onEdit, onRemove, onShare, photos, comments, bookOn)
            }
        }
    }
}

@Composable
private fun Content(
    state: RecipeDetailUiState.Ready,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    photos: @Composable () -> Unit,
    comments: @Composable () -> Unit,
    bookOn: Boolean,
) {
    val recipe = state.recipe
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        // R4: the title, « De … · Pour 6 personnes », the category then the tags, and the little story.
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                val servings = recipe.servings?.let { pluralStringResource(R.plurals.recipe_servings, it, it) }
                val line = listOfNotNull(state.origin?.let { stringResource(R.string.recipe_from, it) }, servings).joinToString(" · ")
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge, color = muted)
                RecipeChips(recipe)
                if (bookOn && recipe.story.isNotBlank()) {
                    Text(stringResource(R.string.recipe_story_quote, recipe.story.trim()), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = muted)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipe_photos_title))
            photos()
        }
        // What to buy comes first in the kitchen: the point of the screen (DESIGN-11).
        // Tiles with an icon, as in the form (refonte lot 4).
        FormTile(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.recipe_ingredients), Modifier.fillMaxWidth()) {
            recipe.ingredientLines.forEach { line -> Line("•", line) }
        }
        FormTile(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.recipe_steps), Modifier.fillMaxWidth()) {
            recipe.stepLines.forEachIndexed { index, line -> Line("${index + 1}.", line) }
        }
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recipe_written_by, state.authorName ?: recipe.authorName), style = MaterialTheme.typography.bodyMedium, color = muted)
            // Sent as a text, never with the photos (carte EXPERT-6).
            ClayLink(stringResource(R.string.recipe_share), onClick = onShare)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipe_comments_title))
            comments()
        }
        if (state.canManage) {
            // The one filled button of the screen (charte Argile), 56 dp.
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.recipe_edit))
            }
            if (state.removeFailed) {
                Text(stringResource(R.string.recipe_remove_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            RemoveButton(recipe.title, onRemove)
        } else {
            // R5: nobody else, not even the admin principal, changes it (DESIGN-173).
            AuthorOnlyNote()
        }
    }
}

/** The category first, filled, then the free tags (règle 16 du brief); read as one line by TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeChips(recipe: Recipe) {
    val category = stringResource(recipe.category.look.labelRes)
    val all = listOf(category) + recipe.tags
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = all.joinToString(", ") },
    ) {
        Chip(category, filled = true)
        recipe.tags.forEach { Chip(it, filled = false) }
    }
}

@Composable
private fun Chip(text: String, filled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) colors.primary else colors.secondaryContainer,
        contentColor = if (filled) colors.onPrimary else colors.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** « 🔒 Seule la personne qui l'a notée peut la modifier ou la retirer. » */
@Composable
private fun AuthorOnlyNote() {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
            Text(stringResource(R.string.recipe_author_only), style = MaterialTheme.typography.bodyMedium)
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
    // At the very bottom, outlined in pale red (recette commune n° 7).
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = { confirm = true },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, error.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error, containerColor = LocalClayColors.current.card),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.recipe_remove)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.recipe_remove_title, title)) },
            text = { Text(stringResource(R.string.recipe_remove_body)) },
            // R8: « Annuler » and a filled red « Retirer », 56 dp each.
            confirmButton = {
                Button(
                    onClick = { confirm = false; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.recipe_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

internal fun previewRecipeDetail(canManage: Boolean = true) =
    RecipeDetailUiState.Ready(previewRecipes().first(), "Mamie Jeanne", canManage, authorName = "Camille")

@FamiliaPreviews
@Composable
private fun RecipeDetailPreview() {
    FamiliaTheme { Surface { RecipeDetailScreen(previewRecipeDetail(), {}, {}, {}, bookOn = true) } }
}

@FamiliaPreviews
@Composable
private fun RecipeDetailOtherPreview() {
    FamiliaTheme { Surface { RecipeDetailScreen(previewRecipeDetail(canManage = false), {}, {}, {}) } }
}
