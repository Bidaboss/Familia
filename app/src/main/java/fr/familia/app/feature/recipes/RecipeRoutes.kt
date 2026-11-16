package fr.familia.app.feature.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import fr.familia.app.core.model.Member

@Composable
fun RecipesRoute(container: AppContainer?, onOpen: (String) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    val viewModel: RecipesViewModel = viewModel(
        factory = viewModelFactory { initializer { RecipesViewModel(container?.recipeRepository, container?.memberRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecipesScreen(state, viewModel::search, onOpen, onAdd, onBack)
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
                )
            }
        },
    )
    val photos by album.uiState.collectAsStateWithLifecycle()
    val actions = rememberPhotoActions(album)
    RecipeDetailScreen(state, onEdit, viewModel::remove, onBack) {
        val empty = if (photos.canAdd) R.string.recipe_photos_empty_author else R.string.recipe_photos_empty
        AlbumPhotosSection(photos, actions, stringResource(empty), showSeeAll = false)
    }
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
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? RecipeEditUiState.Done)?.let { onSaved(it.recipeId) } }
    RecipeEditScreen(state, viewModel::update, viewModel::save, onBack)
}
