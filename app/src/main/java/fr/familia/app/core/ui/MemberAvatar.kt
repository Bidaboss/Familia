package fr.familia.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.theme.OnMemberColor
import kotlin.math.absoluteValue

/**
 * Avatar in the Argile style: a small sphere of the member's color (lighter at the top left,
 * darker at the bottom right) with a soft shadow, and the initial. The circle grows with the font size so the
 * initial always fits (40 dp at normal size). With a profile photo (PRD, récit 3 bis, from
 * [LocalMemberPhotos]) the photo fills the sphere instead. Hidden from TalkBack: the first name
 * next to it already says who it is.
 */
@Composable
fun MemberAvatar(member: Member, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val color = memberColor(member)
    val photo: ImageBitmap? = LocalMemberPhotos.current[member.id]
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size * fontScale)
                .shadow(3.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape)
                .clearAndSetSemantics {},
        )
        return
    }
    Box(
        modifier = modifier
            .size(size * fontScale)
            .shadow(3.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(color)
            .drawBehind {
                // Light from the top left and shade at the bottom right make a sphere. Both fade out
                // before the center, so the initial keeps its 4.5:1 contrast.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(this.size.width * 0.3f, this.size.height * 0.25f),
                        radius = this.size.minDimension * 0.45f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                        center = Offset(this.size.width * 0.35f, this.size.height * 0.3f),
                        radius = this.size.minDimension * 0.85f,
                    ),
                )
            }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocalMemberInitials.current[member.id] ?: member.firstName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = OnMemberColor,
        )
    }
}

/**
 * The letters of each avatar, by member id (DESIGN-19, [memberInitials]): given for the whole app
 * while the R1 bis presentation is on. Empty, every avatar shows its single initial.
 */
val LocalMemberInitials = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * Same color on every phone for a given member: the one an admin chose, or else one derived from
 * the member id.
 */
fun memberColor(member: Member): Color =
    member.colorIndex?.takeIf { it in MemberColors.indices }?.let { MemberColors[it] }
        ?: MemberColors[member.id.hashCode().absoluteValue % MemberColors.size]
