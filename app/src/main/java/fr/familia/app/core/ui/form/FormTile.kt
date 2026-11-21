package fr.familia.app.core.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.LocalClayColors

/** Corners of the tiles of a form (DESIGN-50), like the cards of the fil. */
val FormTileShape = RoundedCornerShape(22.dp)

/**
 * One question of a form (DESIGN-50, shared by « Nouvel événement » and « Demander un coup de
 * main »): a tile of the card color of the theme, a 5 dp edge of [accent] on the left, then [icon]
 * in a small square and [title] as a heading, and the [content] under it.
 */
@Composable
fun FormTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    /** The small square of the icon and the icon; by default, a pale [accent] and [accent]. */
    iconBackground: Color = accent.copy(alpha = 0.16f),
    iconTint: Color = accent,
    /** At the end of the title row (a pencil, a pill); the title then takes the room left. */
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = FormTileShape,
        color = LocalClayColors.current.card,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clip(FormTileShape)
                .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 21.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .then(if (trailing != null) Modifier.weight(1f) else Modifier)
                        .semantics { heading() },
                )
                trailing?.invoke(this)
            }
            content()
        }
    }
}

/** A small label above a field of a tile (« Le titre », « Adresse »…). */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = modifier)
}

/** The help or the error under a field: the error wins, in the error color. */
@Composable
fun FormHint(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
