package fr.familia.app.feature.trips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.list.TripsTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The colors of the trip forms (DESIGN-56, DESIGN-57): the sky of the trips, tiles edged in light blue. */
internal object TripFormColors {
    val Edge = Color(0xFF9FD3F2)
    val IconBackground = Color(0xFFEAF6FB)
    val Icon = Color(0xFF2E4A57)
}

/** A question of a trip form: a [FormTile] edged in light blue, its icon on pale blue. */
@Composable
internal fun TripTile(icon: ImageVector, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    FormTile(
        icon = icon,
        title = title,
        modifier = modifier,
        accent = TripFormColors.Edge,
        iconBackground = TripFormColors.IconBackground,
        iconTint = TripFormColors.Icon,
        content = content,
    )
}

/**
 * The frame of a trip form: the sky whatever the theme, the round « Retour », [title] and the « ? »
 * in ink, and the black [action] button fixed at the bottom ([action] null: no button). The tiles
 * keep the text color of the theme.
 */
@Composable
internal fun TripFormScaffold(
    title: String,
    help: ScreenHelp,
    onBack: () -> Unit,
    action: String?,
    busy: Boolean,
    onAction: () -> Unit,
    /** Under the map, in its sheet (règle 4): the bar starts at the top of the sheet, not under the status bar. */
    inSheet: Boolean = false,
    /** Fiche F2 §4: over the map, the round back, a pill with [title] over this line (« Voiture · Séville ») and the help. */
    subtitle: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val header = fr.familia.app.feature.trips.map.LocalBackdropHeader.current.takeIf { inSheet && subtitle != null }
    val currentBack = androidx.compose.runtime.rememberUpdatedState(onBack)
    if (header != null) {
        androidx.compose.runtime.DisposableEffect(header, title, subtitle, help) {
            header.content = { MapTitleBar(title, subtitle.orEmpty(), help) { currentBack.value() } }
            onDispose { header.content = null }
        }
    }
    val floating = header != null && fr.familia.app.feature.trips.map.LocalBackdropHeaderFloating.current
    Scaffold(
        containerColor = TripPathColors.Sky,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (!floating) {
                Box(Modifier.background(TripPathColors.Sky).then(if (inSheet) Modifier else Modifier.statusBarsPadding())) {
                    TripsTopBar(onBack = onBack, title = title, help = help)
                }
            }
        },
        bottomBar = {
            if (action != null) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(inverseSurface = TripPathColors.Ink, inverseOnSurface = Color.White),
                ) {
                    StickyActionBar(text = action, onClick = onAction, busy = busy)
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), content = content)
    }
}

/** Over the map (fiche F2 §4): the white round back, the white pill « Nouvel élément » over « Voiture · Séville », the help. */
@Composable
private fun MapTitleBar(title: String, subtitle: String, help: ScreenHelp, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) {
            fr.familia.app.core.ui.BackButton(onClick = onBack)
        }
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.95f),
            contentColor = TripPathColors.Ink,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 6.dp).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.Center) {
                Text(title, fontFamily = fr.familia.app.core.theme.Fredoka, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) {
            fr.familia.app.feature.help.HelpButton(help)
        }
    }
}

private val boxDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRANCE)

/** A box « [label] · the day » that opens the calendar; [initial] is where the calendar opens without a day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDayBox(
    label: String,
    date: LocalDate?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initial: LocalDate? = null,
    /** Opens another calendar instead (« Dates du voyage », fiche F4); null: the calendar of the day. */
    onOpen: (() -> Unit)? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, if (date != null) colors.outline else colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = { if (onOpen != null) onOpen() else open = true })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(
            date?.format(boxDate) ?: stringResource(R.string.trip_pick_day),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (date != null) colors.onSurface else colors.onSurfaceVariant,
        )
    }
    if (open) {
        val utc = (date ?: initial ?: familyToday()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utc)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** « Supprimer … » at the very bottom of a form, outlined in pale red, as for the event. */
@Composable
internal fun TripDeleteButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, error.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error, containerColor = LocalClayColors.current.card),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}
