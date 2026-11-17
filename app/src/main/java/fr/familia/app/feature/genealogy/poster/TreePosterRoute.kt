package fr.familia.app.feature.genealogy.poster

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
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Member
import fr.familia.app.core.print.PRINT_BYTES_PER_PHOTO
import fr.familia.app.core.print.writePdfSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TreePosterRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val defaultTitle = stringResource(R.string.tree_poster_default_title)
    val viewModel: TreePosterViewModel = viewModel(
        key = "tree-poster",
        factory = viewModelFactory {
            initializer {
                TreePosterViewModel(
                    genealogy = container?.genealogyRepository,
                    members = container?.memberRepository,
                    photos = container?.memberPhotoRepository,
                    currentMemberId = currentMember.id,
                    defaultTitle = defaultTitle,
                    year = familyToday().year,
                    print = { content, photo -> printPoster(app, content, photo) },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val made by viewModel.made.collectAsStateWithLifecycle()
    val chooser = stringResource(R.string.tree_poster_share_chooser)
    // The share sheet reads the file after we hand it over, so it cannot go at once: an old poster
    // is removed the next time the screen opens, and at most one is kept in the cache.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { oldPosters(app).forEach { it.delete() } } }
    LaunchedEffect(made) {
        made?.let { file ->
            sharePdf(context, file, chooser)
            viewModel.shared()
        }
    }
    TreePosterScreen(
        state = state,
        actions = TreePosterActions(viewModel::setTitle, viewModel::setStart, viewModel::setWithPhotos, viewModel::setFormat, viewModel::make),
        onBack = onBack,
    )
}

/** Writes the poster in the cache folder shares/ (res/xml/photo_paths.xml), replaced at each poster. */
private suspend fun printPoster(context: Context, content: PosterContent, photo: (String) -> ByteArray?): File = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "shares").apply { mkdirs() }
    oldPosters(context).forEach { it.delete() }
    val file = File(folder, posterFileName(content.year))
    writePdfSafely(file, content.photoCount * PRINT_BYTES_PER_PHOTO) { part -> writeTreePoster(content, part) { photo(it) } }
}

/** The posters left in the cache folder shares/ by an earlier « Faire l'affiche ». */
private fun oldPosters(context: Context): List<File> =
    File(context.cacheDir, "shares").listFiles { f -> f.name.startsWith("arbre-de-la-famille-") && (f.name.endsWith(".pdf") || f.name.endsWith(".pdf.part")) }?.toList().orEmpty()

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
        // No app to receive it: nothing opens, the poster stays in the cache until the next one.
    }
}
