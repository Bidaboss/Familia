package fr.familia.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.theme.OnMemberColor
import kotlin.math.absoluteValue

/**
 * Round avatar with the member's color and initial. The circle grows with the font size so the
 * initial always fits (40 dp at normal size). Hidden from TalkBack: the first name next to it
 * already says who it is.
 */
@Composable
fun MemberAvatar(member: Member, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(
        modifier = modifier
            .size(size * fontScale)
            .clip(CircleShape)
            .background(memberColor(member))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = member.firstName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = OnMemberColor,
        )
    }
}

/**
 * Same color on every phone for a given member: the one an admin chose, or else one derived from
 * the member id.
 */
fun memberColor(member: Member): Color =
    member.colorIndex?.takeIf { it in MemberColors.indices }?.let { MemberColors[it] }
        ?: MemberColors[member.id.hashCode().absoluteValue % MemberColors.size]
