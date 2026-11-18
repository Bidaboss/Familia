package fr.familia.app.feature.members.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing

/**
 * The sections of the member page drawn as their own cards (PRODUIT-115, maquettes P1 à P5). A
 * section from another module (goûts, cadeaux) reads it to draw itself inside one, without a card of
 * its own. Off everywhere else, so those modules keep their look outside the page.
 */
val LocalProfileSection = staticCompositionLocalOf { false }

/** The color of a section: its left edge and the square of its icon. */
enum class SectionAccent(private val light: Color, private val dark: Color) {
    /** Goûts et passions. */
    Rose(Color(0xFFE38AB0), Color(0xFFC86E95)),

    /** Cadeaux. */
    Peach(Color(0xFFF0A878), Color(0xFFCC8556)),

    /** Sa famille, Coordonnées. */
    Lilac(Color(0xFFAE9BE6), Color(0xFF8F7BC9)),

    /** Ses moments, Recettes et souvenirs. */
    Green(Color(0xFF8CC58F), Color(0xFF6BA56F)),
    ;

    @Composable
    fun color(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light
}

/**
 * One section of the page: its icon in a rounded square, its title, an optional pill ([badge]) and,
 * on my page, a pencil ([onEdit], 48 dp), above [content]. A thin line of [accent] runs on its left.
 */
@Composable
fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    accent: SectionAccent,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onEdit: (() -> Unit)? = null,
    editLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = accent.color()
    ClayCard(modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(color, topLeft = Offset.Zero, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(title, icon, color, badge, onEdit, editLabel)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, color: Color, badge: String?, onEdit: (() -> Unit)?, editLabel: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(color.copy(alpha = 0.28f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        badge?.let {
            Surface(shape = RoundedCornerShape(50), color = color) {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2B1D10), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        onEdit?.let { edit ->
            IconButton(onClick = edit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = editLabel ?: title)
            }
        }
    }
}

/**
 * An empty section on my own page (P3): a dashed card, a sentence, and one dark button to fill it.
 * Never on someone else's page, where an empty section does not show (récit 66).
 */
@Composable
fun ProfileInviteCard(title: String, icon: ImageVector, accent: SectionAccent, text: String, action: String, onAction: () -> Unit) {
    val color = accent.color()
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
                )
            }
            .padding(ClaySpacing.card),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(title, icon, color, badge = null, onEdit = null, editLabel = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text(action) }
    }
}

/** A small capital label inside a section: « SES ENVIES (6) », « ENFANT DE ». */
@Composable
fun ProfileSectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The beige box of what stays private on a page (« Ce que je lui offre », « Mes cadeaux à offrir »). */
@Composable
fun ProfilePrivateBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
