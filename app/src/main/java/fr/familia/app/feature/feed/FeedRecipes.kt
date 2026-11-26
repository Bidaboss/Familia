package fr.familia.app.feature.feed

import fr.familia.app.core.data.gazetteThreadId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.recipes.recipePhotoFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The recipes added since [since], newest first, [limit] at most; one just sent waits for its server time. */
fun newRecipes(recipes: List<Recipe>, since: Instant, limit: Int): List<Recipe> =
    recipes.filter { it.createdAt?.isBefore(since) == false }
        .sortedWith(compareByDescending<Recipe> { it.createdAt }.thenBy { it.id })
        .take(limit)

/**
 * « Nouvelle recette » (récit 67): the recipes added to the book, read like the Recettes screen
 * (every member reads every recipe), with the photo of the dish and the last comment.
 */
class RecipeFeedSource(
    private val recipes: RecipeRepository,
    private val photos: AlbumPhotoRepository?,
) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        recipes.observeRecipes()
            .map { list -> newRecipes(list, window.since, window.limit).map { RecipeFeedCard(it, recipes, photos) } }
            .catch { emit(emptyList()) }
}

class RecipeFeedCard(
    val recipe: Recipe,
    private val recipes: RecipeRepository,
    private val photos: AlbumPhotoRepository?,
) : FeedCard {
    override val key: String = "recipe:${recipe.id}"
    override val at: Instant = recipe.createdAt ?: Instant.EPOCH
    override val authorId: String = recipe.authorId

    /** J'aime on its thread; its comments stay those of the recipe (récit 66). */
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key), own = RecipeOwnComments(recipes, recipe.id), commentable = false)

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Peach
        val photoFlow = remember(recipe.id, photos) {
            photos?.observePhotos(recipePhotoFolder(recipe.id))?.map { it.firstOrNull() }?.catch { emit(null) } ?: flowOf(null)
        }
        val photo by photoFlow.collectAsStateWithLifecycle(initialValue = null)
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenRecipe(recipe.id) },
            header = {
                FeedHeader(
                    member = context.member(recipe.authorId),
                    name = recipe.authorName,
                    action = stringResource(R.string.feed_action_recipe),
                    emoji = "🍳",
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                )
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                photo?.let { StorageImage(it.thumbnailPath, null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) }
                Column(Modifier.weight(1f)) {
                    Text(
                        recipe.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recipe.origin.trim().takeIf { it.isNotEmpty() }?.let { origin ->
                        Text(
                            stringResource(R.string.feed_recipe_origin, origin),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
