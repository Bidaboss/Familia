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

/** What the cover shows, in the order of PRODUIT-153: the photo, else the satellite view, else the drawn landscape. */
internal sealed interface CoverLayer {
    data class Photo(val photo: PlacePhoto) : CoverLayer
    data class Satellite(val url: String) : CoverLayer
    data object Drawn : CoverLayer
}

/** The still satellite view of [point] from Mapbox (a public token), or null without token or point. */
internal fun satelliteStillUrl(point: GeoPoint?, token: String): String? {
    if (point == null || token.isBlank()) return null
    val lng = "%.4f".format(java.util.Locale.ROOT, point.lng)
    val lat = "%.4f".format(java.util.Locale.ROOT, point.lat)
    return "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/$lng,$lat,10.5,0/640x400@2x?access_token=$token"
}

/** The layer after [current] when its image cannot be shown. */
internal fun nextLayer(current: CoverLayer, satellite: String?): CoverLayer = when (current) {
    is CoverLayer.Photo -> satellite?.let { CoverLayer.Satellite(it) } ?: CoverLayer.Drawn
    is CoverLayer.Satellite, CoverLayer.Drawn -> CoverLayer.Drawn
}

/**
 * The image of the place of a trip (règles 10 and 12): a real photo from Wikimedia Commons with its
 * credit at the bottom, else the satellite view of the place, else the drawn [landscape] (no
 * network, no photo). Plain landscape while the photo is looked for.
 */
@Composable
fun PlaceCover(
    place: String?,
    point: GeoPoint?,
    landscape: TripLandscape,
    photos: PlacePhotos?,
    token: String,
    modifier: Modifier = Modifier,
    showCredit: Boolean = true,
) {
    val satellite = remember(point, token) { satelliteStillUrl(point, token) }
    var layer by remember(place, point) { mutableStateOf<CoverLayer?>(null) }
    LaunchedEffect(place, point, photos) {
        val found = place?.takeIf { it.isNotBlank() }?.let { photos?.of(it) }
        layer = found?.let { CoverLayer.Photo(it) } ?: satellite?.let { CoverLayer.Satellite(it) } ?: CoverLayer.Drawn
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
                    onError = { layer = nextLayer(shown, satellite) },
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
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            is CoverLayer.Satellite -> AsyncImage(
                model = shown.url,
                contentDescription = stringResource(R.string.place_cover_satellite, place.orEmpty()),
                contentScale = ContentScale.Crop,
                onError = { layer = nextLayer(shown, satellite) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
