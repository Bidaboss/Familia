package fr.familia.app.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** What the cover shows (DESIGN-104 A): the photo of the place, else the drawn landscape; never a satellite view. */
internal sealed interface CoverLayer {
    data class Photo(val photo: PlacePhoto) : CoverLayer
    data object Drawn : CoverLayer
}

/**
 * The image of the place of a trip (règles 10 and 12): a real photo from Wikimedia Commons with its
 * credit at the bottom, else the drawn [landscape] (no network, no photo). Never a view of the map
 * (DESIGN-104 A: « une jolie photo de la première destination, pas une vue map »). Plain landscape
 * while the photo is looked for.
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
                    onError = { layer = CoverLayer.Drawn },
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
