package fr.familia.app.feature.albums.book

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.book.BookSource
import fr.familia.app.core.album.book.BookStep
import fr.familia.app.core.album.book.PhotoBookActions
import fr.familia.app.core.album.book.PhotoBookContent
import fr.familia.app.core.album.book.PhotoBookDestination
import fr.familia.app.core.album.book.PhotoBookScreen
import fr.familia.app.core.album.book.PhotoBookViewModel
import fr.familia.app.core.album.book.bookDates
import fr.familia.app.core.album.book.photoBookFileName
import fr.familia.app.core.album.book.writePhotoBook
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.StepType
import fr.familia.app.core.photo.PhotoSizing
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.print.PRINT_BYTES_PER_PHOTO
import fr.familia.app.core.print.writePdfSafely
import fr.familia.app.core.share.ShareConsentReminder
import fr.familia.app.feature.events.album.eventAlbumFolder
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import fr.familia.app.feature.trips.stepTypeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/** « Faire le livre photo » of the album of a trip or an event (récit 59). */
@Composable
fun PhotoBookRoute(container: AppContainer?, album: PhotoBookDestination, onBack: () -> Unit) {
    if (!album.isKnown) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val app = context.applicationContext
    // The full photos are kept only while this screen is open: making the book again reuses
    // them, and leaving the screen frees the room (a big album weighs hundreds of Mo).
    DisposableEffect(Unit) {
        onDispose { thread(name = "photo-book-cache") { bookPhotos(app).deleteRecursively() } }
    }
    val labels = StepType.entries.associateWith { stepTypeLabel(it) }
    val viewModel: PhotoBookViewModel = viewModel(
        key = "photo-book-${album.kind}-${album.id}",
        factory = viewModelFactory {
            initializer {
                PhotoBookViewModel(
                    photos = container?.albumPhotoRepository,
                    source = { bookSource(container, album, labels) },
                    print = { content -> printBook(app, content, container?.photoStorage) },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val made by viewModel.made.collectAsStateWithLifecycle()
    val chooser = stringResource(R.string.photo_book_share_chooser)
    // Carte EXPERT-6: the reminder about the parents' agreement comes before the share sheet.
    made?.let { file ->
        ShareConsentReminder(
            onContinue = {
                sharePdf(context, file, chooser)
                viewModel.shared()
            },
            onDismiss = viewModel::shared,
            body = stringResource(R.string.photo_book_consent_body),
        )
    }
    PhotoBookScreen(
        state = state,
        actions = PhotoBookActions(
            onTitle = viewModel::setTitle,
            onFormat = viewModel::setFormat,
            onToggle = viewModel::toggle,
            onMove = viewModel::move,
            onBig = viewModel::toggleBig,
            onCover = viewModel::setCover,
            onMake = viewModel::make,
        ),
        onBack = onBack,
    )
}

/** The title, the dates and, for a trip, the steps, oldest first. */
private suspend fun bookSource(container: AppContainer?, album: PhotoBookDestination, labels: Map<StepType, String>): BookSource? = when (album.kind) {
    PhotoBookDestination.TRIP -> container?.tripRepository?.let { trips ->
        trips.getTrip(album.id)?.let { trip ->
            val steps = trips.observeSteps(album.id).first()
                .sortedWith(compareBy({ it.departDate }, { it.departTime }))
                .map { BookStep(labels.getValue(it.type), it.place, it.departDate) }
            BookSource(tripAlbumFolder(trip.id), trip.title, bookDates(trip.startDate, trip.endDate), steps)
        }
    }
    PhotoBookDestination.EVENT -> container?.eventRepository?.observeEvent(album.id)?.first()?.let { event ->
        BookSource(eventAlbumFolder(event.id), event.title, bookDates(event.date))
    }
    else -> null
}

/**
 * Writes the book in the cache folder shares/ (res/xml/photo_paths.xml). Each full photo is
 * downloaded once into photo-book/ and read from there: making the book again sends nothing more.
 */
private suspend fun printBook(context: Context, content: PhotoBookContent, storage: PhotoStorage?): File = withContext(Dispatchers.IO) {
    val shares = File(context.cacheDir, "shares").apply { mkdirs() }
    oldBooks(context).forEach { it.delete() }
    val photos = bookPhotos(context).apply { mkdirs() }
    val file = File(shares, photoBookFileName(content.title))
    // Each photo is kept once in photo-book/ (about 300 KB) and drawn in the PDF.
    val needed = content.photos.size * (PRINT_BYTES_PER_PHOTO + PhotoSizing.PHOTO_TARGET_BYTES)
    writePdfSafely(file, needed) { part -> writePhotoBook(content, part) { photo -> cachedPhoto(photos, photo, storage) } }
}

/** The full photos of the book being made, removed when the screen closes. */
private fun bookPhotos(context: Context): File = File(context.cacheDir, "photo-book")

private suspend fun cachedPhoto(folder: File, photo: AlbumPhoto, storage: PhotoStorage?): ByteArray? {
    val cached = File(folder, "${photo.folder.replace('/', '_')}_${photo.id}.jpg")
    try {
        if (cached.isFile && cached.length() > 0) return cached.readBytes()
    } catch (e: IOException) {
        // Removed meanwhile: downloaded again below.
    }
    val bytes = storage?.bytes(photo.photoPath) ?: return null
    try {
        cached.writeBytes(bytes)
    } catch (e: IOException) {
        // No room or folder gone: the book is still made, only without the copy for next time.
    }
    return bytes
}

/** The books left in the cache folder shares/ by an earlier « Faire le livre photo ». */
private fun oldBooks(context: Context): List<File> =
    File(context.cacheDir, "shares").listFiles { f -> f.name.startsWith("livre-photo") && (f.name.endsWith(".pdf") || f.name.endsWith(".pdf.part")) }?.toList().orEmpty()

/** Opens the share sheet of Android: save it, print it or send it to a photo shop. */
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
