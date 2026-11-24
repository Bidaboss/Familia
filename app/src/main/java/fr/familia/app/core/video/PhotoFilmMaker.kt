package fr.familia.app.core.video

import android.content.Context
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
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
interface PhotoFilmMaker {
    /** Writes to [output] an MP4 showing [photoUrls] in that order, [secondsPerPhoto] each, in the [style] asked. */
    suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File, style: FilmStyle = FilmStyle.Album)
}

/**
 * How a film looks. [Album]: the photos still, as the albums always had them. [Trip] (récit 108,
 * règle 32): a slow zoom on each photo and a bounded bit rate, about 1,5 Mbit/s, so that a film of
 * a step stays light enough to be sent (3 to 9 Mo for 15 to 45 s).
 */
data class FilmStyle(
    /** How much bigger each photo is at its end than at its start; 1 for none. */
    val zoomTo: Float = 1f,
    /** Bits per second of the video; null leaves the choice to the encoder of the phone. */
    val bitrate: Int? = null,
) {
    companion object {
        val Album = FilmStyle()
        val Trip = FilmStyle(zoomTo = 1.08f, bitrate = 1_500_000)
    }
}

/** The zoom of a photo [elapsedUs] into a film of photos [photoUs] long each: from 1 to [zoomTo], again at each photo. */
fun filmZoomAt(elapsedUs: Long, photoUs: Long, zoomTo: Float): Float {
    if (photoUs <= 0L || zoomTo == 1f) return 1f
    val progress = (elapsedUs.mod(photoUs)).toFloat() / photoUs
    return 1f + (zoomTo - 1f) * progress
}

/** The film through Media3 Transformer: each photo fills a 1280 × 720 frame, borders kept. */
@OptIn(UnstableApi::class)
class Media3PhotoFilmMaker(context: Context) : PhotoFilmMaker {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun make(photoUrls: List<String>, secondsPerPhoto: Double, output: File, style: FilmStyle) {
        require(photoUrls.isNotEmpty())
        output.parentFile?.mkdirs()
        output.delete()
        val durationMs = (secondsPerPhoto * 1000).toLong()
        // Photos of any shape and size: each one is fitted in the same frame.
        val fit = Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)
        val photoUs = durationMs * 1000L
        // The slow zoom of the trips (récit 108): the time is taken modulo a photo, whether Media3 counts it from the photo or from the film.
        val zoom = MatrixTransformation { timeUs ->
            val scale = filmZoomAt(timeUs, photoUs, style.zoomTo)
            Matrix().apply { postScale(scale, scale) }
        }
        val frame = Effects(emptyList(), if (style.zoomTo == 1f) listOf(fit) else listOf(fit, zoom))
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
                val builder = Transformer.Builder(appContext)
                style.bitrate?.let { bitrate ->
                    builder.setEncoderFactory(
                        DefaultEncoderFactory.Builder(appContext)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                            .build(),
                    )
                }
                val transformer = builder
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
