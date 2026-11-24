package fr.familia.app.feature.trips.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.place.Place
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.BackButton
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.list.GlobeFlight
import fr.familia.app.feature.trips.list.GlobeMarker
import fr.familia.app.feature.trips.list.TripsGlobe
import fr.familia.app.feature.trips.list.talkBackOn
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.GlobeHealth

/** The grey blue of « Continuer » while no place is chosen (fiche F4). */
private val ContinueOff = Color(0xFF8E979D)
private val Night = Brush.verticalGradient(listOf(Color(0xFF0B1F45), Color(0xFF050B1A)))

/**
 * « Où allez-vous ? » (récit 95, variante A de DESIGN-65; fiche F4): one question, the destination of
 * the trip, searched by the phone while the member types (up to 5 places). Stateless; « Je tape un nom »
 * leaves without a place. The satellite globe fills the top on the night of the list (récit 110): it
 * turns until a place is chosen, then flies over it; the light blue sheet at the bottom asks. Its grip
 * raises it to the whole screen, and the keyboard raises it too. Without a [globeToken], with TalkBack
 * or when the globe failed lately, the sheet alone on the light blue.
 */
@Composable
fun TripDestinationScreen(
    query: String,
    search: DestinationSearch,
    chosen: Place?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (Place) -> Unit,
    onContinue: () -> Unit,
    onTypeAName: () -> Unit,
    onBack: () -> Unit,
    globeToken: String = "",
) {
    val context = LocalContext.current
    var globeFailed by rememberSaveable { mutableStateOf(false) }
    val globeAllowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    val globe = globeToken.isNotBlank() && !globeFailed && globeAllowed
    // Each new choice flies again; the id only changes with the place.
    val flight = remember(chosen) { chosen?.let { GlobeFlight(it.hashCode(), "", GeoPoint(it.lat, it.lng)) } }
    var raised by rememberSaveable { mutableStateOf(false) }
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BoxWithConstraints(Modifier.fillMaxSize().background(if (globe) Night else SolidColor(TripPathColors.Sky))) {
        val full = !globe || raised || keyboard
        val lowered = minOf(maxOf(maxHeight * 0.44f, 360.dp), maxHeight)
        val sheetHeight = if (full) maxHeight else lowered
        if (globe) {
            TripsGlobe(
                token = globeToken,
                markers = chosen?.let { listOf(GlobeMarker(GeoPoint(it.lat, it.lng), highlighted = true)) }.orEmpty(),
                souvenirs = false,
                stopped = false,
                flight = flight,
                onFailed = {
                    GlobeHealth.markFailed(context)
                    globeFailed = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // The same size raised or not: the map is not laid out again under the sheet.
                    .height(maxHeight - lowered + 28.dp)
                    .clearAndSetSemantics { },
            )
        }
        Column(Modifier.fillMaxSize()) {
            TopRow(onBack, onDark = globe && !full)
            Spacer(Modifier.weight(1f))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(TripPathColors.Sky)
                .then(if (full) Modifier.statusBarsPadding() else Modifier)
                .imePadding(),
        ) {
            if (full && globe) {
                TopRow(onBack, onDark = false)
            }
            if (globe && !keyboard) {
                Grip(raised = full, onToggle = { raised = !raised })
            } else if (!globe) {
                TopRow(onBack, onDark = false)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.trip_destination_title),
                    fontFamily = Fredoka,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TripPathColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                SearchField(query, onQueryChange, onSearch)
                when (search) {
                    DestinationSearch.Searching -> SheetHint(stringResource(R.string.trip_destination_searching))
                    DestinationSearch.NothingFound -> SheetHint(stringResource(R.string.trip_destination_none), isError = true)
                    is DestinationSearch.Found -> Surface(shape = RoundedCornerShape(20.dp), color = Color.White, contentColor = TripPathColors.Ink) {
                        Column(Modifier.selectableGroup().padding(horizontal = 8.dp)) {
                            search.places.forEach { place ->
                                PlaceRow(place, selected = place == chosen, onPick = { onPick(place) })
                            }
                        }
                    }
                    DestinationSearch.Idle -> SheetHint(stringResource(R.string.trip_destination_hint))
                }
                // Not in the prototype: without it, a trip without a place (a wedding…) could not be made.
                TextButton(
                    onClick = onTypeAName,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
                ) {
                    Text(stringResource(R.string.trip_destination_type_name), fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline)
                }
            }
            Box(Modifier.fillMaxWidth().background(TripPathColors.Sky).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = onContinue,
                    enabled = chosen != null,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TripPathColors.Ink,
                        contentColor = Color.White,
                        disabledContainerColor = ContinueOff,
                        disabledContentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.trip_destination_continue), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

/** A hint on the light blue sheet, in its ink whatever the theme (the theme's grey is pale in dark). */
@Composable
private fun SheetHint(text: String, isError: Boolean = false) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
        color = if (isError) Color(0xFFB3261E) else TripPathColors.Ink.copy(alpha = 0.8f),
        modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    )
}

/** The white round back, « Nouveau voyage » (white over the night, ink on the light blue) and the white round help. */
@Composable
private fun TopRow(onBack: () -> Unit, onDark: Boolean) {
    Row(
        Modifier.fillMaxWidth().then(if (onDark) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) { BackButton(onClick = onBack) }
        Text(
            stringResource(R.string.trip_destination_new),
            fontFamily = Fredoka,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (onDark) Color.White else TripPathColors.Ink,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) { HelpButton(ScreenHelp.TripEdit) }
    }
}

/** The grip of the sheet: a touch raises it to the whole screen, another lowers it back over the globe. */
@Composable
private fun Grip(raised: Boolean, onToggle: () -> Unit) {
    val label = stringResource(if (raised) R.string.trip_destination_sheet_lower else R.string.trip_destination_sheet_raise)
    Box(
        Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 44.dp, height = 5.dp).background(Color(0xFF9FB2BD), RoundedCornerShape(3.dp)))
    }
}

/** The white search of 56 with its magnifier (fiche F4); the places come while typing. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val label = stringResource(R.string.trip_destination_placeholder)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = TripPathColors.Ink),
        cursorBrush = SolidColor(TripPathColors.Ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = TripPathColors.Ink)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(label, fontSize = 16.sp, color = TripPathColors.Ink.copy(alpha = 0.6f))
                    field()
                }
            }
        },
    )
}

/** One proposed place, at least 56 dp high, read as one choice by TalkBack. */
@Composable
private fun PlaceRow(place: Place, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(start = 4.dp)) {
            Icon(if (selected) Icons.Filled.Check else Icons.Outlined.Place, contentDescription = null)
        }
        Text(place.title.orEmpty(), fontSize = 16.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal, modifier = Modifier.weight(1f))
    }
}
