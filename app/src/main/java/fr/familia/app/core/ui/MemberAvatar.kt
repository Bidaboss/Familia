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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.theme.OnMemberColor
import kotlin.math.absoluteValue

/** Round avatar with the member's color and initial (40 dp by default). Decorative for TalkBack. */
@Composable
fun MemberAvatar(member: Member, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(memberColor(member.id)),
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
 * Same color on every phone for a given member. Until members get a stored color (lot 3c), it is
 * derived from the member id.
 */
fun memberColor(memberId: String) = MemberColors[memberId.hashCode().absoluteValue % MemberColors.size]
