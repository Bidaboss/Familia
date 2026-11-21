package fr.familia.app.feature.trips.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.drawLandscape
import fr.familia.app.feature.trips.detail.palette

/** The size of the touch target of a point, as for every control of the app. */
private val PointTarget = 56.dp

/**
 * The map of a trip (récit 87): the placed [stops] joined in order by a dashed path, each with its
 * number. A point is a 56 dp button; the chosen one is yellow and bigger. The ground under the
 * points is [background]: the landscape of the trip today ([LandscapeBackground]), a map of
 * streets one day. The points come from [projectToUnit], whatever the background.
 */
@Composable
fun TripMap(
    stops: List<MapStop>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    landscape: TripLandscape,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = { LandscapeBackground(landscape) },
) {
    val placed = stops.filter { it.point != null }
    val palette = landscape.palette
    val description = pluralStringResource(R.plurals.trip_map_placed, placed.size, placed.size)
    BoxWithConstraints(
        modifier
            .aspectRatio(MAP_ASPECT)
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = description },
    ) {
        background()
        Canvas(Modifier.fillMaxSize()) {
            if (placed.size < 2) return@Canvas
            val offsets: List<Offset> = placed.map { Offset(it.point!!.x * size.width, it.point.y * size.height) }
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            // A light ribbon with a dashed line on it, as the path of the trip.
            drawPath(path, palette.road, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(
                path,
                palette.dash,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                ),
            )
        }
        placed.forEach { stop ->
            val point = stop.point ?: return@forEach
            MapPoint(
                stop = stop,
                selected = stop.step.id == selectedId,
                onClick = { onSelect(stop.step.id) },
                modifier = Modifier.offset(maxWidth * point.x - PointTarget / 2, maxHeight * point.y - PointTarget / 2),
            )
        }
    }
}

/** The landscape of the trip as the ground of the map (the background of today). */
@Composable
fun BoxScope.LandscapeBackground(landscape: TripLandscape) {
    Canvas(Modifier.fillMaxSize()) { drawLandscape(landscape) }
}

@Composable
private fun MapPoint(stop: MapStop, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.trip_map_point, stop.number, stop.total, stop.step.place)
    Box(
        modifier
            .size(PointTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .defaultMinSize(if (selected) 48.dp else 38.dp, if (selected) 48.dp else 38.dp)
                .clip(CircleShape)
                .background(if (selected) TripPathColors.Sun else Color.White)
                .border(BorderStroke(if (selected) 3.dp else 2.dp, TripPathColors.Ink), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stop.number.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TripPathColors.Ink,
            )
        }
    }
}
