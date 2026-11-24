package fr.familia.app.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.detail.TripPathColors

/** The bottom button of a [TripPage]: the one full button of the screen (fiche F3). */
class TripPageAction(val text: String, val onClick: () -> Unit, val enabled: Boolean = true, val busy: Boolean = false)

/**
 * A page of a trip as the prototype draws them (fiches F3, F4): the light blue of the trips whatever
 * the theme, the white round back, the title in Fredoka with its [subtitle] (« Valise » over the trip),
 * the white round help when there is one, and the dark [action] fixed at the bottom.
 */
@Composable
internal fun TripPage(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    help: ScreenHelp? = null,
    action: TripPageAction? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        containerColor = TripPathColors.Sky,
        contentColor = TripPathColors.Ink,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(TripPathColors.Sky).statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) { BackButton(onClick = onBack) }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        title,
                        fontFamily = Fredoka,
                        fontSize = if (subtitle == null) 22.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TripPathColors.Ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (help != null) Surface(shape = CircleShape, color = Color.White, contentColor = TripPathColors.Ink, shadowElevation = 2.dp) { HelpButton(help) }
            }
        },
        bottomBar = {
            if (action != null) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(inverseSurface = TripPathColors.Ink, inverseOnSurface = Color.White)) {
                    StickyActionBar(text = action.text, onClick = action.onClick, enabled = action.enabled, busy = action.busy)
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().heightIn(min = 0.dp), content = content)
    }
}

/** The white card of a [TripPage] (fiche F3: radius 26), in the ink of the trips. */
@Composable
internal fun TripPageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = Color.White, contentColor = TripPathColors.Ink, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}

/**
 * The 24 dp dark box of a line (fiche F3), drawn only: the whole line is the toggle, at least 56 dp
 * high, and says it is a checkbox.
 */
@Composable
internal fun TripPageCheck(checked: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(24.dp)
            .background(if (checked) TripPathColors.Ink else Color.White, shape)
            .border(2.dp, TripPathColors.Ink, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** « + Ajouter une ligne » at the foot of a card (fiche F3: 14 sp, 800), 56 dp high. */
@Composable
internal fun TripPageLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
    }
}
