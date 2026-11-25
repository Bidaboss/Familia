package fr.familia.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R

/** The rise of the round above the bar (DESIGN-55, variante A). */
val ALL_ROUND_RISE: Dp = 18.dp

/** The yellow of the count of news, as on the former « Tout Familia » bar. */
private val NEWS_YELLOW = Color(0xFFF2C14E)
private val NEWS_INK = Color(0xFF1B1B1B)

/**
 * « Tout Familia » at the center of the bar (DESIGN-55, variante A, l'admin 20/11): a dark round of
 * 64 dp with a ring of 5 dp of the bar color and the yellow count of [news]. No label; TalkBack
 * reads « Tout Familia, 3 nouveautés ».
 */
@Composable
fun AllFamiliaRound(news: Int, onClick: () -> Unit, modifier: Modifier = Modifier, ring: Color = MaterialTheme.colorScheme.surfaceContainer) {
    val title = stringResource(R.string.home_r1_all)
    val spoken = if (news > 0) "$title, ${pluralStringResource(R.plurals.home_r1_all_news, news, news)}" else title
    val dark = MaterialTheme.colorScheme.inverseSurface
    Box(
        modifier
            .size(74.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        Box(
            Modifier
                .size(74.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(ring)
                .padding(5.dp)
                .clip(CircleShape)
                .background(dark)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(30.dp))
        }
        if (news > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(24.dp)
                    .border(2.dp, ring, CircleShape)
                    .clip(CircleShape)
                    .background(NEWS_YELLOW),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (news > 9) "9+" else news.toString(), color = NEWS_INK, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
