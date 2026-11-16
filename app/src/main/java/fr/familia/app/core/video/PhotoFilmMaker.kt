package fr.familia.app.core.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Makes a silent film from photos, on the phone (récit 16): nothing is sent to a server. Shared by
 * the film of a trip, and later the album of an event and the slideshow of the month.
 */
fun interface PhotoFilmMaker {
    /** Writes to [output] an MP4 showing [photoUrls] in that order, [secondsPerPhoto] each. */
    suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File)
}

/** The film through Media3 Transformer: each photo fills a 1280 × 720 frame, borders kept. */
@OptIn(UnstableApi::class)
class Media3PhotoFilmMaker(context: Context) : PhotoFilmMaker {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File) {
        require(photoUrls.isNotEmpty())
        output.parentFile?.mkdirs()
        output.delete()
        val durationMs = (secondsPerPhoto * 1000).toLong()
        // Photos of any shape and size: each one is fitted in the same frame.
        val frame = Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)))
        val items = photoUrls.map { url ->
            val media = MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.IMAGE_JPEG)
                .setImageDurationMs(durationMs)
                .build()
            EditedMediaItem.Builder(media).setFrameRate(FRAME_RATE).setEffects(frame).build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence(items)).build()

        // Transformer is driven from a thread with a Looper: the main one.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(appContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            continuation.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            output.delete()
                            continuation.resumeWithException(IOException("Film not made", exportException))
                        }
                    })
                    .build()
                continuation.invokeOnCancellation {
                    // Called on any thread; Transformer wants the one it was started on.
                    mainHandler.post {
                        transformer.cancel()
                        output.delete()
                    }
                }
                transformer.start(composition, output.absolutePath)
            }
        }
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val FRAME_RATE = 30
    }
}
