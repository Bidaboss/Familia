package fr.familia.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.theme.PhotoTitleShadow
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.ui.frenchDate
import java.time.LocalDate
import java.util.Locale

/** The cream of the date under the greeting (A1: rgb(255,243,226)). */
private val DateCream = Color(0xFFFFF3E2)

/**
 * The top of the Accueil on its photo (récit 113, règle 2, fiche A1 §1.1): the olive tree of the app
 * in its red ring, « Bonjour Camille 👋 » and the date in white, then « + » and [avatar], which opens
 * the settings (the « ? » is there now, règle 8). It scrolls with the Accueil.
 */
@Composable
fun PhotoHomeHeader(firstName: String, today: LocalDate, onAdd: (() -> Unit)?, avatar: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background))) {
            // The foreground is drawn on the 108 grid: its middle 72 is what the launcher shows.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(72.dp).align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            val title = MaterialTheme.typography.titleLarge
            Text(
                stringResource(R.string.home_photo_greeting, firstName),
                style = title.copy(fontSize = 21.sp, lineHeight = 26.sp, shadow = PhotoTitleShadow),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                frenchDate(today, "EEEE d MMMM").replaceFirstChar { it.titlecase(Locale.FRANCE) },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, shadow = PhotoTitleShadow),
                fontWeight = FontWeight.Bold,
                color = DateCream,
            )
        }
        if (onAdd != null) {
            // A 40 dp round, white at 92 %, in a 56 dp target (règle 5).
            IconButton(onClick = onAdd, modifier = Modifier.size(56.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_r1_add), tint = Color(0xFF231A16))
                }
            }
        }
        avatar()
    }
}

/** The brown of the texts on the cream of the photo cards (A1: rgb(90,58,46)). */
internal val PhotoBrown = Color(0xFF5A3A2E)

/**
 * « Ma journée | Le fil » on the photo (récit 113, règle 3, A1 §1.3): a frosted bar of 54 dp, the
 * chosen half in the dark color of the theme with a white text, the number of news on « Le fil » in
 * the accent color.
 */
@Composable
fun PhotoFeedSwitch(onFeed: Boolean, unseen: Int, accent: Color, dark: Color, onChoose: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .photoCard(24.dp)
            .selectableGroup()
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Segment(stringResource(R.string.home_photo_my_day), chosen = !onFeed, dark = dark, onClick = { onChoose(false) }, modifier = Modifier.weight(1f))
        Segment(stringResource(R.string.feed_tab_feed), chosen = onFeed, dark = dark, onClick = { onChoose(true) }, modifier = Modifier.weight(1f)) {
            if (unseen > 0) {
                val news = pluralStringResource(R.plurals.feed_tab_new, unseen, unseen)
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .semantics { contentDescription = news },
                ) {
                    Text(unseen.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}

@Composable
private fun Segment(
    text: String,
    chosen: Boolean,
    dark: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (chosen) dark else Color.Transparent)
            .selectable(selected = chosen, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (chosen) Color.White else PhotoBrown, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        badge()
    }
}
