package fr.familia.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The small label above a block, in capitals (R1 bis, §1): 13 sp at least, never less, and a
 * heading for TalkBack so the blocks can be jumped through. The text is written normally in the
 * strings (« À faire ») and shown in capitals here, so TalkBack does not spell it.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}
