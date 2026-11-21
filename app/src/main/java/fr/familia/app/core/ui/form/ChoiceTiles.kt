package fr.familia.app.core.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.LocalClayColors

private val ChoiceTileShape = RoundedCornerShape(16.dp)

/**
 * Columns of [ChoiceTiles]: [wanted] at the normal size, 2 at most from 130 % of text, 1 from 180 %.
 */
internal fun choiceColumns(wanted: Int, fontScale: Float): Int = when {
    fontScale >= 1.8f -> 1
    fontScale >= 1.3f -> minOf(wanted, 2)
    else -> wanted
}

/**
 * A few choices as tiles (refonte, lot 3: the radios of Apparence and of the fiche): [columns]
 * a row, the last row keeping the width of the others. A radio group for TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceTiles(
    options: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (option: T, modifier: Modifier) -> Unit,
) {
    val perRow = choiceColumns(columns, LocalDensity.current.fontScale)
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = perRow,
    ) {
        options.forEach { tile(it, Modifier.weight(1f)) }
        repeat((perRow - options.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * One choice: [top] (color dots, a picture), its [title] in [titleStyle], and a short [subtitle].
 * Chosen, it takes the pale color of the theme, a dark edge and a check in its corner; a radio
 * button of at least 64 dp for TalkBack.
 */
@Composable
fun ChoiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    top: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(ChoiceTileShape)
            .background(if (selected) colors.secondaryContainer else LocalClayColors.current.card)
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = if (selected) colors.onSurface else colors.outlineVariant,
                shape = ChoiceTileShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 30.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            top?.invoke()
            Text(title, style = titleStyle, fontWeight = if (titleStyle.fontWeight == null) FontWeight.Bold else null, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(colors.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(14.dp))
            }
        }
    }
}
