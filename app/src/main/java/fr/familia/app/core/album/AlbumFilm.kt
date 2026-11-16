package fr.familia.app.core.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.share.ShareConsentReminder
import fr.familia.app.core.share.playVideo
import fr.familia.app.core.share.shareVideo
import java.io.File

/** What the film part can ask for. The defaults do nothing, for previews and screenshots. */
data class FilmActions(
    val onMake: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * « Faire le film » (récit 16), then « Regarder » and « Partager ». Sharing always shows the
 * reminder of EXPERT-6 first.
 */
@Composable
fun AlbumFilmPart(state: FilmState, photoCount: Int, actions: FilmActions) {
    val context = LocalContext.current
    var remind by rememberSaveable { mutableStateOf(false) }
    var noPlayer by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            FilmState.Idle, FilmState.Failed -> {
                FilledTonalButton(
                    onClick = actions.onMake,
                    enabled = photoCount >= FILM_MIN_PHOTOS,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Movie, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.album_film_make))
                }
                Text(
                    stringResource(
                        when {
                            state == FilmState.Failed -> R.string.album_film_failed
                            photoCount < FILM_MIN_PHOTOS -> R.string.album_film_need_photos
                            else -> R.string.album_film_help
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state == FilmState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilmState.Making -> {
                Text(stringResource(R.string.album_film_making), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.album_film_cancel))
                }
            }
            is FilmState.Ready -> {
                Text(stringResource(R.string.album_film_ready), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { noPlayer = !playVideo(context, File(state.path)) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.album_film_watch))
                    }
                    Button(onClick = { remind = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.album_film_share))
                    }
                }
                if (noPlayer) {
                    Text(
                        stringResource(R.string.album_film_no_player),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (remind) {
                    val chooser = stringResource(R.string.album_film_share)
                    ShareConsentReminder(
                        onContinue = {
                            remind = false
                            shareVideo(context, File(state.path), chooser)
                        },
                        onDismiss = { remind = false },
                    )
                }
            }
        }
    }
}
