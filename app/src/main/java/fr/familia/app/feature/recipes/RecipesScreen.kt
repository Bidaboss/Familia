package fr.familia.app.feature.recipes

import fr.familia.app.core.theme.themeBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import fr.familia.app.core.ui.LoadingState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import fr.familia.app.core.model.RecipeCategory

/**
 * The recipe book (R2) or the recipes a member noted (R3): a search field, the recipes grouped by
 * category (« Entrées (2) »), the book at the end, and « Nouvelle recette » only on the book and
 * on my own list (règles 19 et 20 du brief). Stateless.
 */
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
    val member = (state as? RecipesUiState.Ready)?.memberName
    Scaffold(
        // The background of the theme, as on the redone screens (refonte lot 4).
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(if (member != null) stringResource(R.string.recipes_from_title, member) else stringResource(R.string.recipes_family_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Recipes) },
            )
        },
        floatingActionButton = {
            if (state is RecipesUiState.Ready && state.canAdd) {
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
                RecipesUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                RecipesUiState.Failed -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipes_error_title),
                    body = stringResource(R.string.recipes_error_body),
                )
                is RecipesUiState.Ready -> if (state.total == 0 && !state.isMemberList) {
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
            val label = if (state.isMemberList) R.string.recipes_search_member else R.string.recipes_search
            OutlinedTextField(
                value = state.query,
                onValueChange = onSearch,
                label = { Text(stringResource(label)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.total == 0) {
            item(key = "empty") { MemberEmpty(state) }
        } else if (state.groups.isEmpty()) {
            item(key = "none") { Text(stringResource(R.string.recipes_none_found), style = MaterialTheme.typography.bodyLarge) }
        }
        state.groups.forEach { group ->
            item(key = "group-${group.category.key}") { GroupTitle(group) }
            items(group.rows, key = { it.recipe.id }) { row -> RecipeCard(row) { onOpen(row.recipe.id) } }
        }
        if (onMakeBook != null) {
            item(key = "book") { BookCard(onMakeBook) }
        }
    }
}

/** R3b: a member who has not noted any recipe yet, or myself. */
@Composable
private fun MemberEmpty(state: RecipesUiState.Ready) {
    val title = if (state.isMine) stringResource(R.string.recipes_mine_empty_title) else stringResource(R.string.recipes_member_empty_title, state.memberName.orEmpty())
    val body = if (state.isMine) stringResource(R.string.recipes_mine_empty_body) else stringResource(R.string.recipes_member_empty_body)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📖", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clearAndSetSemantics {})
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** « 🥗 Entrées (2) »: a heading, so TalkBack jumps from one category to the next. */
@Composable
private fun GroupTitle(group: RecipeGroup) {
    val look = group.category.look
    val text = pluralStringResource(look.groupRes, group.rows.size, group.rows.size)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { heading() },
    ) {
        Text(look.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecipeCard(row: RecipeRow, onClick: () -> Unit) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val servings = row.recipe.servings?.let { pluralStringResource(R.plurals.recipe_servings, it, it) }
    val details = listOfNotNull(servings).plus(row.recipe.tags).joinToString(" · ")
    ClayCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.recipes_open)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EmojiTile(row.recipe.category.look.emoji)
            Column(Modifier.weight(1f)) {
                // A plain text, not a card title: a heading is its own stop for TalkBack, so a recipe with
                // no origin and no tag left the card itself with nothing to read.
                Text(row.recipe.title, style = MaterialTheme.typography.titleMedium)
                row.origin?.let { Text(stringResource(R.string.recipe_from, it), style = MaterialTheme.typography.bodyMedium, color = muted) }
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
    }
}

/** The emoji of the category in a soft rounded square, decorative: the group title says it. */
@Composable
private fun EmojiTile(emoji: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
    }
}

/** « Faire le livre de recettes », at the end of the list (R2, R3). */
@Composable
private fun BookCard(onClick: () -> Unit) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EmojiTile("📚")
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recipe_book_open), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.recipe_book_open_body), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
    }
}

/** Fictional recipes for previews and screenshots. */
internal fun previewRecipes(): List<Recipe> = listOf(
    Recipe("1", "Gâteau aux pommes", "Mamie Jeanne", null, 6, "4 pommes\n150 g de farine\n3 œufs\n100 g de sucre", "Préchauffer le four à 180 °C.\nCouper les pommes en lamelles.\nMélanger la farine, les œufs et le sucre.\nAjouter les pommes et cuire 40 minutes.", listOf("dessert", "goûter"), "a", "Camille", null, category = RecipeCategory.Dessert),
    Recipe("2", "Soupe de potiron", "", null, 4, "1 potiron\n2 pommes de terre\n1 oignon", "Couper les légumes.\nCuire 30 minutes.\nMixer.", listOf("hiver"), "b", "Inès", null, category = RecipeCategory.Entree),
    Recipe("3", "Crêpes de la Chandeleur", "", null, null, "250 g de farine\n4 œufs\n50 cl de lait", "Mélanger.\nLaisser reposer une heure.\nCuire à la poêle.", emptyList(), "a", "Camille", null, category = RecipeCategory.Dessert),
    Recipe("4", "Gratin dauphinois", "", null, 6, "1 kg de pommes de terre\n50 cl de crème", "Trancher.\nCuire 1 h.", listOf("dimanche"), "c", "Noé", null, category = RecipeCategory.Plat),
)

internal fun previewRecipesState(): RecipesUiState.Ready = previewRecipes().let { list ->
    val rows = list.map { RecipeRow(it, it.origin.ifBlank { it.authorName }) }
    RecipesUiState.Ready(groupByCategory(rows), "", list.size)
}

/** R3b: the list of a member who has not noted any recipe. */
internal fun previewMemberEmptyState(): RecipesUiState.Ready = RecipesUiState.Ready(emptyList(), "", 0, memberName = "Inès", canAdd = false)

@FamiliaPreviews
@Composable
private fun RecipesScreenPreview() {
    FamiliaTheme { Surface { RecipesScreen(previewRecipesState(), {}, {}, {}, {}, onMakeBook = {}) } }
}

@FamiliaPreviews
@Composable
private fun RecipesMemberEmptyPreview() {
    FamiliaTheme { Surface { RecipesScreen(previewMemberEmptyState(), {}, {}, {}, {}, onMakeBook = {}) } }
}
