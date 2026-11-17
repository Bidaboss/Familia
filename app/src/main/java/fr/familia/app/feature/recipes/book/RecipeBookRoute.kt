package fr.familia.app.feature.recipes.book

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun RecipeBookRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val defaultTitle = stringResource(R.string.recipe_book_default_title)
    val viewModel: RecipeBookViewModel = viewModel(
        key = "recipe-book",
        factory = viewModelFactory {
            initializer {
                RecipeBookViewModel(
                    recipes = container?.recipeRepository,
                    members = container?.memberRepository,
                    photos = container?.albumPhotoRepository,
                    currentMemberId = currentMember.id,
                    defaultTitle = defaultTitle,
                    year = LocalDate.now().year,
                    print = { content -> printBook(app, content, container) },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val made by viewModel.made.collectAsStateWithLifecycle()
    val chooser = stringResource(R.string.recipe_book_share_chooser)
    LaunchedEffect(made) {
        made?.let { file ->
            sharePdf(context, file, chooser)
            viewModel.shared()
        }
    }
    RecipeBookScreen(
        state = state,
        actions = RecipeBookActions(viewModel::toggle, viewModel::chooseAll, viewModel::setTitle, viewModel::setCover, viewModel::make),
        onBack = onBack,
    )
}

/** Writes the book in the cache folder shares/ (res/xml/photo_paths.xml), replaced at each book. */
private suspend fun printBook(context: Context, content: BookContent, container: AppContainer?): File = withContext(Dispatchers.IO) {
    val res = context.resources
    val labels = BookLabels(
        contents = context.getString(R.string.recipe_book_contents),
        otherRecipes = context.getString(R.string.recipe_book_other),
        ingredients = context.getString(R.string.recipe_ingredients),
        steps = context.getString(R.string.recipe_steps),
        from = { context.getString(R.string.recipe_from, it) },
        writtenBy = { context.getString(R.string.recipe_written_by, it) },
        servings = { res.getQuantityString(R.plurals.recipe_servings, it, it) },
    )
    val folder = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(folder, bookFileName(content.year))
    val storage = container?.photoStorage
    writeRecipeBook(content, labels, file) { path -> storage?.bytes(path) }
    file
}

/** Opens the share sheet of Android: save it, print it or send it. */
private fun sharePdf(context: Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        // No app to receive it: nothing opens, the book stays in the cache until the next one.
    }
}
