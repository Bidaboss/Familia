package fr.familia.app.feature.recipes

import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** The recipe book: a search field, the recipes by title, and « Nouvelle recette ». Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    state: RecipesUiState,
    onSearch: (String) -> Unit,
    onOpen: (recipeId: String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    /** « Faire le livre de recettes », with the switch recipeBook (récit 47); null hides it. */
    onMakeBook: (() -> Unit)? = null,
) {
    val origin = (state as? RecipesUiState.Ready)?.originName
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (origin != null) stringResource(R.string.recipes_from_title, origin) else stringResource(R.string.recipes_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Recipes) },
            )
        },
        floatingActionButton = {
            if (state is RecipesUiState.Ready) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.recipes_add)) },
                    text = { Text(stringResource(R.string.recipes_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                RecipesUiState.Failed -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipes_error_title),
                    body = stringResource(R.string.recipes_error_body),
                )
                is RecipesUiState.Ready -> if (state.total == 0) {
                    EmptyState(
                        icon = Icons.Outlined.Restaurant,
                        title = stringResource(R.string.recipes_empty_title),
                        body = stringResource(R.string.recipes_empty_body),
                    )
                } else {
                    RecipeList(state, onSearch, onOpen, onMakeBook)
                }
            }
        }
    }
}

@Composable
private fun RecipeList(state: RecipesUiState.Ready, onSearch: (String) -> Unit, onOpen: (String) -> Unit, onMakeBook: (() -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the add button never hides the last recipe.
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onSearch,
                label = { Text(stringResource(R.string.recipes_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onMakeBook != null) {
            item(key = "book") { ClayLink(stringResource(R.string.recipe_book_open), onClick = onMakeBook) }
        }
        if (state.rows.isEmpty()) {
            item(key = "none") { Text(stringResource(R.string.recipes_none_found), style = MaterialTheme.typography.bodyLarge) }
        }
        items(state.rows, key = { it.recipe.id }) { row -> RecipeCard(row) { onOpen(row.recipe.id) } }
    }
}

@Composable
private fun RecipeCard(row: RecipeRow, onClick: () -> Unit) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.recipes_open)) {
        ClayCardTitle(row.recipe.title, style = MaterialTheme.typography.titleMedium)
        row.origin?.let { Text(stringResource(R.string.recipe_from, it), style = MaterialTheme.typography.bodyMedium, color = muted) }
        if (row.recipe.tags.isNotEmpty()) {
            Text(row.recipe.tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
}

/** Fictional recipes for previews and screenshots. */
internal fun previewRecipes(): List<Recipe> = listOf(
    Recipe("1", "Gâteau aux pommes", "Mamie Jeanne", null, 6, "4 pommes\n150 g de farine\n3 œufs\n100 g de sucre", "Préchauffer le four à 180 °C.\nCouper les pommes en lamelles.\nMélanger la farine, les œufs et le sucre.\nAjouter les pommes et cuire 40 minutes.", listOf("dessert", "goûter"), "a", "Camille", null),
    Recipe("2", "Soupe de potiron", "", "b", 4, "1 potiron\n2 pommes de terre\n1 oignon", "Couper les légumes.\nCuire 30 minutes.\nMixer.", listOf("hiver"), "b", "Inès", null),
    Recipe("3", "Crêpes de la Chandeleur", "", null, null, "250 g de farine\n4 œufs\n50 cl de lait", "Mélanger.\nLaisser reposer une heure.\nCuire à la poêle.", emptyList(), "a", "Camille", null),
)

internal fun previewRecipesState(): RecipesUiState.Ready = previewRecipes().let { list ->
    RecipesUiState.Ready(list.map { RecipeRow(it, if (it.originMemberId == "b") "Inès" else it.origin.ifBlank { null }) }, "", list.size)
}

@FamiliaPreviews
@Composable
private fun RecipesScreenPreview() {
    FamiliaTheme { Surface { RecipesScreen(previewRecipesState(), {}, {}, {}, {}) } }
}
