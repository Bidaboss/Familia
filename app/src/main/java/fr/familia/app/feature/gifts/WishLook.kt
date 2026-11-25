package fr.familia.app.feature.gifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

/**
 * The look of the screens of a wish (correctif E6 à E10, fiches K8 et K20): white rounded fields
 * with a beige edge on the cream, dark brown for what is chosen, pastel vignettes.
 */
object WishLook {
    val Edge = Color(0xFFE6DCCB)
    val Field = Color.White
    val Dark = Color(0xFF3A2A1A)
    val Ink = Color(0xFF2B1D12)
    val Muted = Color(0xFF6B5B4B)
    /** The pale blue of « Toute la famille verra cette envie » and of « déjà acheté ». */
    val InfoBlue = Color(0xFFE3ECF3)
    val InfoBlueInk = Color(0xFF1F3A52)
    /** The pale yellow of the reminder on the page of a wish. */
    val InfoYellow = Color(0xFFFBF0C2)
    val Remove = Color(0xFFB3261E)

    val Pastels = listOf(
        Color(0xFFE3F1FA),
        Color(0xFFE6F4E1),
        Color(0xFFFDECE6),
        Color(0xFFEDE7F8),
        Color(0xFFFFF4D6),
    )

    /** The same pastel for the same wish, from its title. */
    fun pastel(seed: String): Color = Pastels[Math.floorMod(seed.hashCode(), Pastels.size)]
}

/** A pill to choose among a few (occasion): dark brown and white when chosen, white with a beige edge else. 56 dp. */
@Composable
fun WishPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (selected) WishLook.Dark else WishLook.Field,
        contentColor = if (selected) Color.White else WishLook.Ink,
        border = if (selected) null else BorderStroke(2.dp, WishLook.Edge),
        modifier = modifier
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.heightIn(min = 56.dp).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
