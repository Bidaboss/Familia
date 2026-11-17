package fr.familia.app.feature.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhotosSection
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.core.share.shareText

@Composable
fun RecipesRoute(
    container: AppContainer?,
    originMemberId: String?,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onMakeBook: () -> Unit = {},
) {
    val viewModel: RecipesViewModel = viewModel(
        key = "recipes-$originMemberId",
        factory = viewModelFactory { initializer { RecipesViewModel(container?.recipeRepository, container?.memberRepository, originMemberId) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The whole book, so not on the list of one member's recipes.
    // Not in « Voir comme »: the book would leave with the photo rights of the member looked at.
    val bookOn = LocalFeatureFlags.current.isOn(Feature.RecipeBook) && originMemberId == null && LocalViewAs.current.viewed == null
    RecipesScreen(state, viewModel::search, onOpen, onAdd, onBack, onMakeBook = if (bookOn) onMakeBook else null)
}

/** A removed recipe closes its page. */
@Composable
fun RecipeDetailRoute(container: AppContainer?, currentMember: Member, recipeId: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val viewModel: RecipeDetailViewModel = viewModel(
        key = "recipe-$recipeId",
        factory = viewModelFactory {
            initializer { RecipeDetailViewModel(container?.recipeRepository, container?.memberRepository, currentMember.id, recipeId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == RecipeDetailUiState.Removed) onBack() }
    val album: AlbumPhotosViewModel = viewModel(
        key = "recipe-photos-$recipeId",
        factory = viewModelFactory {
            initializer {
                val recipes = container?.recipeRepository
                val members = container?.memberRepository
                AlbumPhotosViewModel(
                    photos = container?.albumPhotoRepository,
                    rights = if (recipes != null && members != null) recipePhotoRights(recipes, members, recipeId, currentMember.id) else null,
                    uploader = container?.photoUploader,
                    storage = container?.photoStorage,
                    currentMemberId = currentMember.id,
                    folder = recipePhotoFolder(recipeId),
                    maxPerMember = RECIPE_PHOTOS,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val photos by album.uiState.collectAsStateWithLifecycle()
    val actions = rememberPhotoActions(album)
    val commentsViewModel: RecipeCommentsViewModel = viewModel(
        key = "recipe-comments-$recipeId",
        factory = viewModelFactory {
            initializer {
                RecipeCommentsViewModel(
                    container?.recipeRepository,
                    container?.memberRepository,
                    currentMember.id,
                    recipeId,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val comments by commentsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val labels = RecipeShareLabels(
        ingredients = stringResource(R.string.recipe_ingredients),
        steps = stringResource(R.string.recipe_steps),
        from = { context.getString(R.string.recipe_from, it) },
        servings = { context.resources.getQuantityString(R.plurals.recipe_servings, it, it) },
    )
    val chooser = stringResource(R.string.recipe_share_chooser)
    RecipeDetailScreen(
        state = state,
        onEdit = onEdit,
        onRemove = viewModel::remove,
        onBack = onBack,
        photos = {
            val empty = if (photos.canAdd) R.string.recipe_photos_empty_author else R.string.recipe_photos_empty
            AlbumPhotosSection(photos, actions, stringResource(empty), showSeeAll = false)
        },
        comments = { RecipeCommentsSection(comments, commentsViewModel::type, commentsViewModel::send, commentsViewModel::remove) },
        onShare = {
            (state as? RecipeDetailUiState.Ready)?.let { ready -> shareText(context, recipeShareText(ready.recipe, ready.origin, labels), chooser) }
        },
        bookOn = LocalFeatureFlags.current.isOn(Feature.RecipeBook),
    )
}

@Composable
fun RecipeEditRoute(container: AppContainer?, currentMember: Member, recipeId: String?, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: RecipeEditViewModel = viewModel(
        key = "recipe-edit-$recipeId",
        factory = viewModelFactory {
            initializer {
                RecipeEditViewModel(
                    container?.recipeRepository,
                    container?.memberRepository,
                    currentMember.id,
                    recipeId,
                    savedState = createSavedStateHandle(),
                    photos = container?.albumPhotoRepository,
                    uploader = container?.photoUploader,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? RecipeEditUiState.Done)?.let { onSaved(it.recipeId) } }
    RecipeEditScreen(
        state,
        viewModel::update,
        viewModel::save,
        onBack,
        showStory = LocalFeatureFlags.current.isOn(Feature.RecipeBook),
        photos = RecipeEditPhotoActions(viewModel::addPhotos, viewModel::removePhoto),
    )
}
