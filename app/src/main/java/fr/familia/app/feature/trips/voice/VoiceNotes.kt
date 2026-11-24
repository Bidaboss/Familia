package fr.familia.app.feature.trips.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.familia.app.R
import fr.familia.app.core.data.TripVoiceNote
import fr.familia.app.core.data.TripVoiceNotes
import kotlinx.coroutines.delay
import java.io.File

/** « 0:42 », « 2:00 ». */
fun voiceDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** The seconds of a recording of [elapsedMillis], rounded up, within 1 and [TripVoiceNotes.MAX_SECONDS]. */
fun voiceSeconds(elapsedMillis: Long): Int =
    ((elapsedMillis + 999) / 1000).toInt().coerceIn(1, TripVoiceNotes.MAX_SECONDS)

/**
 * The microphone, in AAC mono at 48 kb/s (about 720 KB for 2 min): it stops by itself at
 * [TripVoiceNotes.MAX_SECONDS] or near [TripVoiceNotes.MAX_BYTES], whatever comes first.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    val recording: Boolean get() = recorder != null

    fun start(file: File, onLimit: () -> Unit) {
        cancel()
        file.delete()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(22_050)
            r.setAudioEncodingBitRate(48_000)
            r.setMaxDuration(TripVoiceNotes.MAX_SECONDS * 1000)
            // A margin for the header of the file.
            r.setMaxFileSize(TripVoiceNotes.MAX_BYTES - 64 * 1024)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimit()
            }
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            throw e
        }
        recorder = r
        startedAt = SystemClock.elapsedRealtime()
    }

    /** The seconds recorded, or 0 when nothing could be kept (stopped at once). */
    fun stop(): Int {
        val r = recorder ?: return 0
        recorder = null
        val seconds = voiceSeconds(SystemClock.elapsedRealtime() - startedAt)
        val kept = runCatching { r.stop() }.isSuccess
        r.release()
        return if (kept) seconds else 0
    }

    fun cancel() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
    }
}

private sealed interface Recording {
    data object Idle : Recording
    data object On : Recording
    data class Done(val seconds: Int) : Recording
    data object Failed : Recording
}

/**
 * « Note vocale » (récit 104, règle 35): record up to 2 min, listen to nothing, send or start
 * again. The microphone is asked for the first time only. The note goes under [destination].
 */
@Composable
fun VoiceNoteDialog(destination: String, onSend: (file: File, seconds: Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "voice-note.m4a") }
    val recorder = remember { VoiceRecorder(context) }
    var state by remember { mutableStateOf<Recording>(Recording.Idle) }
    var elapsed by remember { mutableIntStateOf(0) }
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    fun stop() {
        val seconds = recorder.stop()
        state = if (seconds > 0) Recording.Done(seconds) else Recording.Failed
    }
    fun begin() {
        state = try {
            recorder.start(file) { stop() }
            elapsed = 0
            Recording.On
        } catch (e: Exception) {
            Recording.Failed
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) begin()
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(state) {
        while (state == Recording.On) {
            delay(1_000)
            elapsed = (elapsed + 1).coerceAtMost(TripVoiceNotes.MAX_SECONDS)
        }
    }
    val current = state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_title, destination)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (current) {
                        Recording.Idle -> stringResource(if (allowed) R.string.voice_ready else R.string.voice_needs_mic, voiceDuration(TripVoiceNotes.MAX_SECONDS))
                        Recording.On -> stringResource(R.string.voice_recording, voiceDuration(elapsed), voiceDuration(TripVoiceNotes.MAX_SECONDS))
                        is Recording.Done -> stringResource(R.string.voice_done, voiceDuration(current.seconds))
                        Recording.Failed -> stringResource(R.string.voice_failed)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(stringResource(R.string.voice_quiet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            when (current) {
                Recording.On -> TextButton(onClick = { stop() }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.voice_stop)) }
                is Recording.Done -> TextButton(onClick = { onSend(file, current.seconds) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.voice_send))
                }
                else -> TextButton(
                    onClick = { if (allowed) begin() else ask.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.voice_record)) }
            }
        },
        dismissButton = {
            if (current is Recording.Done) {
                TextButton(onClick = { begin() }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.voice_again)) }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** One voice note playing at a time, from its link; released when the screen goes. */
@Stable
class VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun play(note: TripVoiceNote, url: String) {
        stop()
        val p = MediaPlayer()
        player = p
        playingId = note.id
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
            p.setDataSource(url)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        playingId = null
    }
}

@Composable
fun rememberVoicePlayer(): VoicePlayer {
    val player = remember { VoicePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}
