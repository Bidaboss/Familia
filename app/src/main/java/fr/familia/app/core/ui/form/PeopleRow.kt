package fr.familia.app.core.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.theme.OnMemberColor
import fr.familia.app.core.ui.LocalMemberPhotos
import kotlin.math.absoluteValue

/**
 * One round of [PeopleRow]: a group with its [icon] on [tint] (« Toute la famille », « Mon foyer »),
 * or a member by [memberId], with their photo, else their initial on their color.
 */
data class PersonChoice(
    val key: String,
    val label: String,
    val selected: Boolean,
    val icon: ImageVector? = null,
    val tint: Color? = null,
    val memberId: String? = null,
    val colorIndex: Int? = null,
)

/**
 * « Pour qui » in rounds (DESIGN-50): every choice seen at once, a photo or an icon, the name under
 * it, a check on the chosen ones. [multiple] false makes it a radio group (one choice), true a list
 * of boxes to tick (« Qui prévenir »). [onToggle] gets the [PersonChoice.key].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleRow(
    choices: List<PersonChoice>,
    multiple: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.then(if (multiple) Modifier else Modifier.selectableGroup()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice -> PersonRound(choice, multiple) { onToggle(choice.key) } }
    }
}

@Composable
private fun PersonRound(choice: PersonChoice, multiple: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val round = 56.dp
    val pick = if (multiple) {
        Modifier.toggleable(value = choice.selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = choice.selected, role = Role.RadioButton, onClick = onClick)
    }
    Column(
        pick.width(76.dp * scale).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            val ring = if (choice.selected) Modifier.border(3.dp, colors.onSurface, CircleShape) else Modifier
            Box(ring.padding(3.dp)) { RoundFace(choice, round) }
            if (choice.selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.inverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = colors.inverseOnSurface, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            choice.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (choice.selected) FontWeight.Bold else FontWeight.Normal,
            color = if (choice.selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun RoundFace(choice: PersonChoice, size: androidx.compose.ui.unit.Dp) {
    val photo = choice.memberId?.let { LocalMemberPhotos.current[it] }
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        return
    }
    val background = choice.tint ?: choice.memberId?.let { id ->
        choice.colorIndex?.takeIf { it in MemberColors.indices }?.let { MemberColors[it] }
            ?: MemberColors[id.hashCode().absoluteValue % MemberColors.size]
    } ?: MaterialTheme.colorScheme.surfaceVariant
    val ink = if (choice.icon != null) MaterialTheme.colorScheme.onSurface else OnMemberColor
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        if (choice.icon != null) {
            Icon(choice.icon, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp))
        } else {
            Text(choice.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}
