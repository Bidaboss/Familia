package fr.familia.app.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.core.place.PlacePhoto
import fr.familia.app.core.place.PlacePhotos
import fr.familia.app.feature.trips.list.MiniLandscape
import fr.familia.app.feature.trips.map.GeoPoint
import kotlinx.coroutines.launch

/** What the cover shows (DESIGN-104 A): the photo of the place, else the drawn landscape; never a satellite view. */
internal sealed interface CoverLayer {
    data class Photo(val photo: PlacePhoto) : CoverLayer
    data object Drawn : CoverLayer
}

/**
 * The image of the place of a trip (règles 10 and 12): a real photo from Wikimedia Commons with its
 * credit at the bottom, else the drawn [landscape] (no network, no photo). Never a view of the map
 * (DESIGN-104 A: « une jolie photo de la première destination, pas une vue map »). Plain landscape
 * while the photo is looked for. When the image itself cannot be shown (retour E-47), the cause is
 * written to the log and the next nearby photo is tried once before the drawing.
 */
@Composable
fun PlaceCover(
    place: String?,
    point: GeoPoint?,
    landscape: TripLandscape,
    photos: PlacePhotos?,
    modifier: Modifier = Modifier,
    showCredit: Boolean = true,
    /** Where the credit goes: top right alone, top left under the label of the carousel (its pause button is top right). */
    creditAlignment: Alignment = Alignment.TopEnd,
    creditTop: androidx.compose.ui.unit.Dp = 6.dp,
) {
    var layer by remember(place, point) { mutableStateOf<CoverLayer?>(null) }
    var retried by remember(place, point) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(place, point, photos) {
        val found = place?.takeIf { it.isNotBlank() }?.let { photos?.of(it, point?.lat, point?.lng) }
        layer = found?.let { CoverLayer.Photo(it) } ?: CoverLayer.Drawn
    }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        when (val shown = layer) {
            null, CoverLayer.Drawn -> MiniLandscape(landscape, Modifier.fillMaxSize())
            is CoverLayer.Photo -> {
                val description = stringResource(R.string.place_cover_photo, place.orEmpty())
                AsyncImage(
                    model = shown.photo.url,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    onError = { error ->
                        Log.w(TAG, "Photo du lieu non affichée : ${shown.photo.url}", error.result.throwable)
                        // Retour du 24/11 soir: the reason of the image itself goes to « Plantages » too.
                        fr.familia.app.core.place.PhotoFailureLog.record(context, "image ${coverErrorLine(error.result.throwable)}")
                        val failed = shown.photo.url
                        if (retried || point == null || photos == null) {
                            layer = CoverLayer.Drawn
                        } else {
                            retried = true
                            scope.launch {
                                val next = photos.near(point.lat, point.lng, except = failed)
                                layer = nextCoverLayer(failed, next)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (showCredit) {
                    Text(
                        stringResource(R.string.place_cover_credit, shown.photo.credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(creditAlignment)
                            .padding(start = 6.dp, end = 6.dp, top = creditTop, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private const val TAG = "PlaceCover"

/** The error of an image, short and without its address: « HttpException: HTTP 403 ». */
internal fun coverErrorLine(error: Throwable): String =
    listOfNotNull(error.javaClass.simpleName, error.message?.substringBefore(" http")?.take(30)).joinToString(": ").take(50)

/** After a failed image: the next nearby photo when there is one and it is another image, else the drawing. */
internal fun nextCoverLayer(failedUrl: String, next: PlacePhoto?): CoverLayer =
    next?.takeIf { it.url != failedUrl }?.let { CoverLayer.Photo(it) } ?: CoverLayer.Drawn
