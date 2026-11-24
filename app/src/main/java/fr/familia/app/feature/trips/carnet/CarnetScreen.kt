package fr.familia.app.feature.trips.carnet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.model.TravelPlace
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.LoadingState
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The colors of the carnet (règle 36): « moins jaune », the cream, night blue and coral of the
 * prototype, whatever the theme, as the inside of a trip.
 */
internal object CarnetColors {
    val Page = Color(0xFFFBF3E6)
    val Ink = Color(0xFF1F3B4D)
    val Soft = Color(0xFF56696F)
    val Coral = Color(0xFFD9604A)
    val Peach = Color(0xFFF3A98B)
    val Sand = Color(0xFFEAD9C4)
    val Dash = Color(0xFFE8CDB8)
    val Sea = Color(0xFFCFE3E2)
    val Paper = Color(0xFFFFFBF3)
    val Stamps = listOf(Color(0xFFB5452E), Color(0xFF2E6B4F), Color(0xFF2F4F7F), Color(0xFF8A5A14), Color(0xFF6B3F7A), Color(0xFF1F6F78))
}

private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
private val TILTS = listOf(-6f, 4f, -3f, 5f)

/** « Carnet de baroudeur », stateless. [onAdd] null: no adding (« Voir comme »). */
@Composable
fun CarnetScreen(
    state: CarnetUiState,
    onBack: () -> Unit,
    onAdd: (() -> Unit)?,
    onRemove: (TravelPlace) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        containerColor = CarnetColors.Page,
        contentColor = CarnetColors.Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { CarnetTopBar(onBack) },
        floatingActionButton = {
            if (onAdd != null && (state as? CarnetUiState.Ready)?.canAdd == true) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = CarnetColors.Ink,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.carnet_add), fontWeight = FontWeight.ExtraBold) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        },
    ) { padding ->
        when (state) {
            CarnetUiState.Loading -> LoadingState(Modifier.padding(padding))
            is CarnetUiState.Ready -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Hero(state.carnet)
                Passport(state.carnet)
                Continents(state.carnet)
                Counter(state.carnet)
                state.carnet.farthest?.let { Farthest(it) }
                Trophies(state.carnet)
                if (state.manual.isNotEmpty()) Manual(state.manual, onRemove = if (onAdd != null) onRemove else null)
            }
        }
    }
}

@Composable
private fun CarnetTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CarnetColors.Page).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(shape = CircleShape, color = Color.White, contentColor = CarnetColors.Ink) { BackButton(onClick = onBack) }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.carnet_title),
                fontFamily = Fredoka,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = CarnetColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.carnet_subtitle), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CarnetColors.Soft)
        }
    }
}

/** The name of [rank] on screen. */
@Composable
internal fun rankName(rank: CarnetRank): String = stringResource(
    when (rank) {
        CarnetRank.Hiker -> R.string.carnet_rank_hiker
        CarnetRank.Explorer -> R.string.carnet_rank_explorer
        CarnetRank.GlobeTrotter -> R.string.carnet_rank_globetrotter
        CarnetRank.GreatTraveller -> R.string.carnet_rank_great
    },
)

/** The postcard at the top: a drawn landscape, the compass, the rank, the countries and the way to the next rank. */
@Composable
private fun Hero(carnet: Carnet) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CarnetColors.Paper)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Landscape(Modifier.fillMaxSize())
            Compass(Modifier.align(Alignment.TopEnd).padding(12.dp).size(64.dp))
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.carnet_rank).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = CarnetColors.Soft, letterSpacing = 1.5.sp)
            Text(rankName(carnet.rank), fontFamily = Fredoka, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = CarnetColors.Coral)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Text(carnet.countries.toString(), fontFamily = Fredoka, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, color = CarnetColors.Ink)
                Text(stringResource(R.string.carnet_countries_label, carnet.worldPercent), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
            }
            val goal = carnet.next?.countries ?: carnet.countries
            val progress = stringResource(R.string.carnet_progress, carnet.countries, goal)
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(goal.coerceAtLeast(1)) { k ->
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (k < carnet.countries) CarnetColors.Coral else CarnetColors.Sand))
                }
            }
            Text(
                carnet.next?.let { stringResource(R.string.carnet_next, it.countries - carnet.countries, rankName(it)) } ?: stringResource(R.string.carnet_max),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CarnetColors.Soft,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A drawn evening on the sea, with the van of the prototype left out: a sky, a sun, hills, the sea. */
@Composable
private fun Landscape(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF7D9C0), CarnetColors.Peach, Color(0xFFE98A6B))))
        drawCircle(Color(0xFFFBE3C4).copy(alpha = 0.9f), radius = h * 0.2f, center = Offset(w * 0.69f, h * 0.61f))
        val far = Path().apply {
            moveTo(0f, h * 0.65f); lineTo(w * 0.11f, h * 0.47f); lineTo(w * 0.19f, h * 0.6f); lineTo(w * 0.31f, h * 0.37f)
            lineTo(w * 0.44f, h * 0.65f); lineTo(w * 0.44f, h * 0.8f); lineTo(0f, h * 0.8f); close()
        }
        drawPath(far, Color(0xFFB9788A).copy(alpha = 0.55f))
        val near = Path().apply {
            moveTo(w * 0.5f, h * 0.75f); lineTo(w * 0.64f, h * 0.49f); lineTo(w * 0.75f, h * 0.69f); lineTo(w * 0.89f, h * 0.52f)
            lineTo(w, h * 0.75f); lineTo(w, h * 0.85f); lineTo(w * 0.5f, h * 0.85f); close()
        }
        drawPath(near, Color(0xFF9C6479).copy(alpha = 0.6f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF6DB1B8), Color(0xFF3F8B98)), startY = h * 0.72f, endY = h), topLeft = Offset(0f, h * 0.72f))
        listOf(0.76f to 0.55f..0.78f, 0.81f to 0.61f..0.89f, 0.87f to 0.53f..0.72f).forEach { (y, xs) ->
            drawLine(Color(0xFFFBE3C4).copy(alpha = 0.6f), Offset(w * xs.start, h * y), Offset(w * xs.endInclusive, h * y), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun Compass(modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(CarnetColors.Page, r * 0.9f, c)
        drawCircle(CarnetColors.Ink, r * 0.9f, c, style = Stroke(2.5.dp.toPx()))
        val north = Path().apply { moveTo(c.x, c.y - r * 0.75f); lineTo(c.x + r * 0.17f, c.y); lineTo(c.x - r * 0.17f, c.y); close() }
        val south = Path().apply { moveTo(c.x, c.y + r * 0.75f); lineTo(c.x + r * 0.17f, c.y); lineTo(c.x - r * 0.17f, c.y); close() }
        drawPath(south, CarnetColors.Ink)
        drawPath(north, CarnetColors.Coral)
        drawCircle(CarnetColors.Page, r * 0.07f, c)
    }
}

/** One part of the carnet: a white card with a dashed border and an upper-case title. */
@Composable
private fun CarnetPart(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)
            .border(BorderStroke(2.dp, CarnetColors.Dash), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title.uppercase(),
            fontFamily = Fredoka,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = CarnetColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Two columns, one at 130 % font size and more. */
@Composable
private fun columns(normal: Int): Int = if (LocalDensity.current.fontScale >= 1.3f) 1 else normal

@Composable
private fun Passport(carnet: Carnet) {
    CarnetPart(stringResource(R.string.carnet_passport)) {
        val n = columns(2)
        val cells: List<Stamp?> = carnet.stamps + listOf(null)
        cells.chunked(n).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { col, stamp ->
                    val i = row * n + col
                    if (stamp == null) EmptyStamp(Modifier.weight(1f)) else StampCell(stamp, CarnetColors.Stamps[i % CarnetColors.Stamps.size], TILTS[i % TILTS.size], Modifier.weight(1f))
                }
                if (chunk.size < n) Spacer(Modifier.weight((n - chunk.size).toFloat()))
            }
        }
    }
}

@Composable
private fun StampCell(stamp: Stamp, color: Color, tilt: Float, modifier: Modifier) {
    val name = countryName(stamp.code)
    val date = stamp.first?.format(monthYear) ?: stringResource(R.string.carnet_home)
    val described = stringResource(R.string.carnet_stamp, name, date)
    Column(
        modifier.padding(4.dp).rotate(tilt).clip(RoundedCornerShape(14.dp)).background(CarnetColors.Paper)
            .border(BorderStroke(3.dp, color), RoundedCornerShape(14.dp)).padding(vertical = 8.dp, horizontal = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = described },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(flagOf(stamp.code), fontSize = 30.sp)
        Text(name.uppercase(), color = color, fontFamily = Fredoka, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(date.uppercase(), color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyStamp(modifier: Modifier) {
    Column(
        modifier.padding(4.dp).heightIn(min = 88.dp).clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(2.dp, Color(0xFF9A8A6A).copy(alpha = 0.7f)), RoundedCornerShape(14.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("?", fontSize = 28.sp, color = Color(0xFF9A8A6A))
        Text(stringResource(R.string.carnet_coming).uppercase(), color = Color(0xFF9A8A6A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun continentName(continent: Continent): String = stringResource(
    when (continent) {
        Continent.Europe -> R.string.carnet_europe
        Continent.Africa -> R.string.carnet_africa
        Continent.America -> R.string.carnet_america
        Continent.Asia -> R.string.carnet_asia
        Continent.Oceania -> R.string.carnet_oceania
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Continents(carnet: Carnet) {
    CarnetPart(stringResource(R.string.carnet_continents)) {
        val visited = Continent.entries.filter { it in carnet.continents }
        val names = visited.map { continentName(it) }
        val description = stringResource(
            R.string.carnet_map_description,
            names.joinToString(", ").ifEmpty { stringResource(R.string.carnet_map_none) },
            carnet.tripPoints.size,
            carnet.manualPoints.size,
        )
        Planisphere(carnet, Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(14.dp)).semantics { contentDescription = description })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { Chip(it, filled = true) }
            Chip(stringResource(R.string.carnet_continents_count, visited.size), filled = false)
        }
    }
}

@Composable
private fun Chip(text: String, filled: Boolean) {
    Text(
        text,
        color = if (filled) Color.White else CarnetColors.Soft,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .then(if (filled) Modifier.background(CarnetColors.Ink) else Modifier.border(BorderStroke(2.dp, CarnetColors.Dash), RoundedCornerShape(12.dp)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The rough outline of each continent, longitude and latitude, for the planisphere only. */
private val OUTLINES: Map<Continent, List<List<Pair<Int, Int>>>> = mapOf(
    Continent.America to listOf(
        listOf(-165 to 65, -140 to 70, -95 to 72, -60 to 60, -55 to 48, -80 to 25, -97 to 16, -80 to 8, -105 to 20, -125 to 40, -130 to 55, -165 to 60),
        listOf(-80 to 10, -60 to 8, -35 to -7, -40 to -22, -58 to -38, -70 to -55, -75 to -40, -70 to -18, -81 to -5),
        listOf(-55 to 60, -20 to 70, -25 to 82, -60 to 82, -73 to 77),
    ),
    Continent.Europe to listOf(
        listOf(-10 to 36, -9 to 44, -5 to 48, -2 to 51, 5 to 58, 10 to 63, 25 to 71, 40 to 68, 45 to 55, 40 to 45, 28 to 41, 20 to 40, 12 to 38, 3 to 43),
    ),
    Continent.Africa to listOf(
        listOf(-17 to 21, -10 to 30, -5 to 36, 10 to 37, 32 to 31, 43 to 12, 51 to 12, 40 to -5, 40 to -15, 33 to -26, 20 to -35, 12 to -17, 9 to 4, -8 to 5, -17 to 14),
    ),
    Continent.Asia to listOf(
        listOf(28 to 41, 40 to 45, 45 to 55, 40 to 68, 70 to 73, 110 to 77, 140 to 72, 180 to 68, 160 to 60, 140 to 50, 122 to 40, 122 to 25, 108 to 10, 103 to 1, 95 to 15, 80 to 8, 72 to 20, 57 to 25, 45 to 13, 35 to 30),
    ),
    Continent.Oceania to listOf(
        listOf(113 to -22, 130 to -12, 142 to -10, 153 to -27, 146 to -39, 135 to -35, 115 to -34),
        listOf(172 to -35, 178 to -38, 170 to -46, 167 to -45),
    ),
)

/**
 * The planisphere (règle 38): the continents visited in coral, the places of the trips as a dot,
 * the hand-added destinations as a simple mark, no line between them and no kilometre.
 */
@Composable
private fun Planisphere(carnet: Carnet, modifier: Modifier) {
    Canvas(modifier.background(CarnetColors.Sea)) {
        fun at(lng: Double, lat: Double) = Offset(((lng + 180) / 360 * size.width).toFloat(), ((90 - lat) / 180 * size.height).toFloat())
        val step = 6.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.45f), 1.2.dp.toPx(), Offset(x, y)); x += step
            }
            y += step
        }
        OUTLINES.forEach { (continent, shapes) ->
            val color = if (continent in carnet.continents) Color(0xFFE98A6B) else CarnetColors.Sand
            shapes.forEach { points ->
                val path = Path()
                points.forEachIndexed { i, (lng, lat) -> at(lng.toDouble(), lat.toDouble()).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                path.close()
                drawPath(path, color)
            }
        }
        carnet.tripPoints.forEach { (lat, lng) ->
            val p = at(lng, lat)
            drawCircle(Color.White, 5.dp.toPx(), p)
            drawCircle(CarnetColors.Ink, 3.5.dp.toPx(), p)
        }
        carnet.manualPoints.forEach { (lat, lng) ->
            val p = at(lng, lat)
            val r = 5.dp.toPx()
            val mark = Path().apply { moveTo(p.x, p.y - r); lineTo(p.x + r, p.y); lineTo(p.x, p.y + r); lineTo(p.x - r, p.y); close() }
            drawPath(mark, Color.White)
            drawPath(mark, CarnetColors.Coral, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Counter(carnet: Carnet) {
    val number = NumberFormat.getIntegerInstance(Locale.FRENCH)
    CarnetPart(stringResource(R.string.carnet_counter)) {
        val cells = listOf(
            number.format(carnet.days) to pluralStringResource(R.plurals.carnet_days, carnet.days),
            number.format(carnet.km) to stringResource(R.string.carnet_km),
            number.format(carnet.trips) to pluralStringResource(R.plurals.carnet_trips, carnet.trips),
            number.format(carnet.photos) to pluralStringResource(R.plurals.carnet_photos, carnet.photos),
        )
        val n = columns(2)
        cells.chunked(n).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (value, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CarnetColors.Page).padding(horizontal = 12.dp, vertical = 10.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text(value, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = CarnetColors.Soft)
                    }
                }
            }
        }
        val laps = String.format(Locale.FRENCH, "%.2f", carnet.laps)
        Text(pluralStringResource(R.plurals.carnet_destinations, carnet.destinations, carnet.destinations, laps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CarnetColors.Soft)
    }
}

@Composable
private fun Farthest(farthest: Farthest) {
    CarnetPart(stringResource(R.string.carnet_farthest)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.carnet_farthest_from), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Canvas(Modifier.weight(1f).height(30.dp)) {
                val path = Path().apply { moveTo(4f, size.height - 4f); quadraticTo(size.width / 2, -size.height * 0.9f, size.width - 4f, size.height - 4f) }
                drawPath(path, CarnetColors.Stamps[0], style = Stroke(2.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
            }
            Text(farthest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.carnet_farthest_km, NumberFormat.getIntegerInstance(Locale.FRENCH).format(farthest.km)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = CarnetColors.Soft,
        )
    }
}

@Composable
private fun Trophies(carnet: Carnet) {
    CarnetPart(stringResource(R.string.carnet_trophies)) {
        val n = columns(3)
        carnet.trophies.chunked(n).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (trophy, won) -> TrophyCell(trophy, won, Modifier.weight(1f)) }
                if (row.size < n) Spacer(Modifier.weight((n - row.size).toFloat()))
            }
        }
    }
}

@Composable
private fun TrophyCell(trophy: Trophy, won: Boolean, modifier: Modifier) {
    val pair: Pair<androidx.compose.ui.graphics.vector.ImageVector, Int> = when (trophy) {
        Trophy.FirstTrip -> Icons.Outlined.Flag to R.string.carnet_trophy_first
        Trophy.ThreeCountries -> Icons.Outlined.Place to R.string.carnet_trophy_three
        Trophy.TwoContinents -> Icons.Outlined.Map to R.string.carnet_trophy_continents
        Trophy.FiveCountries -> Icons.Outlined.Place to R.string.carnet_trophy_five
        Trophy.TenThousandKm -> Icons.Outlined.Flight to R.string.carnet_trophy_km
        Trophy.AroundTheWorld -> Icons.Outlined.EmojiEvents to R.string.carnet_trophy_world
    }
    val (icon, label) = pair
    val name = stringResource(label)
    val described = stringResource(if (won) R.string.carnet_trophy_won else R.string.carnet_trophy_locked, name)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = described },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (won) CarnetColors.Peach else CarnetColors.Sand),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (won) CarnetColors.Ink else CarnetColors.Soft) }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = if (won) CarnetColors.Ink else CarnetColors.Soft,
        )
    }
}

/** The hand-added destinations, each with « Retirer » after a question; [onRemove] null in « Voir comme ». */
@Composable
private fun Manual(places: List<TravelPlace>, onRemove: ((TravelPlace) -> Unit)?) {
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    CarnetPart(stringResource(R.string.carnet_manual)) {
        places.forEach { place ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(place.countryCode?.let(::flagOf).orEmpty(), fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.carnet_manual_line, place.date.format(monthYear), place.days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CarnetColors.Soft,
                    )
                }
                if (onRemove != null) {
                    val described = stringResource(R.string.carnet_remove_described, place.name)
                    TextButton(onClick = { asking = place.id }, modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = described }) {
                        Text(stringResource(R.string.carnet_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    val target = places.firstOrNull { it.id == asking }
    if (target != null && onRemove != null) {
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.carnet_remove_title, target.name)) },
            text = { Text(stringResource(R.string.carnet_remove_body)) },
            confirmButton = { TextButton(onClick = { asking = null; onRemove(target) }) { Text(stringResource(R.string.carnet_remove), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
