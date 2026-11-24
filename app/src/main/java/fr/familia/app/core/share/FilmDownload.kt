package fr.familia.app.core.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** « Télécharger » a film into the gallery (récit 108, règle 32), and what it says after. */
class FilmDownload internal constructor(private val scope: CoroutineScope, private val save: suspend (FilmSource) -> Boolean) {
    enum class State { Idle, Saving, Saved, Failed }

    var state by mutableStateOf(State.Idle)
        private set

    fun download(source: FilmSource) {
        if (state == State.Saving) return
        state = State.Saving
        scope.launch { state = if (save(source)) State.Saved else State.Failed }
    }
}

/** A film made on this phone, or one published in Storage whose address comes when asked. */
sealed interface FilmSource {
    val name: String

    data class Local(val file: File, override val name: String) : FilmSource
    class Published(val url: suspend () -> String?, override val name: String) : FilmSource
}

/** Null on Android 8 and 9, where the gallery needs a permission: « Télécharger » is then not offered. */
@Composable
fun rememberFilmDownload(): FilmDownload? {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    if (!canSaveVideoToGallery()) return null
    return remember(scope) {
        FilmDownload(scope) { source ->
            when (source) {
                is FilmSource.Local -> saveVideoToGallery(context, source.file, source.name)
                is FilmSource.Published -> source.url()?.let { saveVideoUrlToGallery(context, it, source.name) } ?: false
            }
        }
    }
}

/** The line under the film once « Télécharger » was touched. */
@Composable
fun FilmDownloadLine(download: FilmDownload?) {
    val state = download?.state ?: return
    val text = when (state) {
        FilmDownload.State.Idle -> return
        FilmDownload.State.Saving -> R.string.film_downloading
        FilmDownload.State.Saved -> R.string.film_downloaded
        FilmDownload.State.Failed -> R.string.film_download_failed
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (state == FilmDownload.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
