package fr.familia.app.feature.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R

/**
 * « Réglages de la carte » (fiche F7, retour E-56): a cream sheet from the bottom over the darkened
 * map, two sections of white tiles of the same width (Mode de vue: 2D, 3D; Style de carte:
 * Satellite, Rues, DESIGN-155 A), the chosen one with a dark border, and « Enregistrer ». The ✕,
 * a touch outside the sheet and the back of Android cancel.
 */
@Composable
fun MapSettingsSheet(
    look: MapLook,
    tilted: Boolean,
    onLook: (MapLook) -> Unit,
    onTilted: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(SheetCream)
                    // A touch on the sheet itself stays on it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.trip_map_settings),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                        color = SettingsInk,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    val close = stringResource(R.string.photo_viewer_close)
                    Box(
                        Modifier
                            .size(56.dp)
                            .clickable(role = Role.Button, onClickLabel = close, onClick = onCancel)
                            .semantics { contentDescription = close },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = SettingsInk)
                        }
                    }
                }
                SectionTitle(stringResource(R.string.trip_map_view_mode))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTile(stringResource(R.string.trip_map_mode_2d), !tilted, Modifier.weight(1f), onClick = { onTilted(false) }) { Preview(flat = true) }
                    SettingTile(stringResource(R.string.trip_map_mode_3d), tilted, Modifier.weight(1f), onClick = { onTilted(true) }) { Preview(flat = false) }
                }
                SectionTitle(stringResource(R.string.trip_map_style))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingTile(stringResource(R.string.trip_map_look_satellite), look == MapLook.Satellite, Modifier.weight(1f), onClick = { onLook(MapLook.Satellite) }) {
                        StylePreview(Icons.Outlined.Public, Brush.linearGradient(listOf(Color(0xFF2F6B3A), Color(0xFF1E4E7A))), Color.White)
                    }
                    SettingTile(stringResource(R.string.trip_map_look_streets), look == MapLook.Streets, Modifier.weight(1f), onClick = { onLook(MapLook.Streets) }) {
                        StylePreview(Icons.Outlined.Map, Brush.linearGradient(listOf(Color(0xFFF1ECE2), Color(0xFFDCE6EC))), SettingsInk)
                    }
                }
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(SettingsInk)
                        .clickable(role = Role.Button, onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.trip_map_settings_save), color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = SettingsInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.semantics { heading() })
}

/** A white tile of the grid, 88 dp high: its preview above, its name below; chosen = a dark border of 3 dp. */
@Composable
private fun SettingTile(name: String, chosen: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(Color.White)
            .then(if (chosen) Modifier.border(3.dp, SettingsInk, shape) else Modifier)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        preview()
        Text(name, color = SettingsInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun Preview(flat: Boolean) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFDCE6EC), Color(0xFFB8C7D0)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (flat) "2D" else "3D", color = SettingsInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun StylePreview(icon: ImageVector, background: Brush, tint: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

/** F7: the cream of the sheet and the dark ink, fixed whatever the theme (the sheet sits on the map). */
private val SheetCream = Color(0xFFFBF4EA)
private val SettingsInk = Color(0xFF1E1B18)
