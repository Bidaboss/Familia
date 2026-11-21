package fr.familia.app.core.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.LocalClayColors

/**
 * « Supprimer » at the very bottom of a form being edited (refonte globale, recette 7): a card
 * button with a pale red outline, like the one of the event form. The confirmation stays with the
 * screen that calls it.
 */
@Composable
fun FormDeleteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, error.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error, containerColor = LocalClayColors.current.card),
        modifier = modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}
