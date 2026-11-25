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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.LocalClayColors

private val CategoryTileShape = RoundedCornerShape(18.dp)

/** Columns of [CategoryTiles]: 3, or 2 when the text is shown at 130 % or more. */
internal fun categoryColumns(fontScale: Float): Int = if (fontScale >= 1.3f) 2 else 3

/**
 * One category to pick among a few, as tiles with an icon (DESIGN-51, the same drawing as the
 * kinds of a coup de main). A radio group for TalkBack: each tile is a radio button of at least
 * 84 dp. The chosen tile takes [selectedColor] with a dark edge and a check in its corner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CategoryTiles(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val columns = categoryColumns(LocalDensity.current.fontScale)
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = columns,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
                    .clip(CategoryTileShape)
                    .background(if (isSelected) selectedColor else LocalClayColors.current.card)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.5.dp,
                        color = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.outlineVariant,
                        shape = CategoryTileShape,
                    )
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(option) }),
            ) {
                val content = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurface
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon(option), contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        textAlign = TextAlign.Center,
                    )
                }
                if (isSelected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(selectedContentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = selectedColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        // Fill the last row so its tiles keep the width of the others.
        repeat((columns - options.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
    }
}
