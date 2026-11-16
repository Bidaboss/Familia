package fr.familia.app.feature.home

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A module of the Accueil that stands in the row of chips under the blocks (DESIGN-11, PRD rév. 205).
 * [chip] shows itself only while the module has something to say, and calls the toggle it is given
 * when its [card] is set: the card then opens under the row, one at a time. Without a card, the chip
 * opens its screen itself.
 */
class HomeExtra(
    val key: String,
    val chip: @Composable (selected: Boolean, onToggle: () -> Unit) -> Unit,
    val card: (@Composable () -> Unit)? = null,
)

/**
 * One chip of the Accueil, 56 dp tall at least. [selected] is set for a chip that opens its card
 * under the row (read « sélectionné » by TalkBack); null for a chip that opens a screen.
 */
@Composable
fun HomeChip(label: String, onClick: () -> Unit, selected: Boolean? = null, modifier: Modifier = Modifier) {
    val text: @Composable () -> Unit = { Text(label, style = MaterialTheme.typography.labelLarge) }
    if (selected == null) {
        AssistChip(onClick = onClick, label = text, modifier = modifier.heightIn(min = 56.dp))
    } else {
        FilterChip(selected = selected, onClick = onClick, label = text, modifier = modifier.heightIn(min = 56.dp))
    }
}
