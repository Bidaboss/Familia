package fr.familia.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.LocalClayColors

/**
 * The top bar of every screen (récit 83, DESIGN-57), drawn like the bar of the redone forms: no
 * band of color (the screen's own background shows through), the round « Retour », the title in
 * the title font of the theme, on 2 lines at most, and the [actions] (the « ? », « Modifier »…) on
 * a white round at the end. [scrolled] draws a thin line under it once the content passes below.
 * [containerColor] only for a page whose colored band starts under the bar (a member's page, the
 * sky of the illustrated trip), with its [contentColor] for the title.
 */
@Composable
fun FamiliaTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrolled: Boolean = false,
    containerColor: Color = Color.Transparent,
    contentColor: Color = Color.Unspecified,
) {
    val card = LocalClayColors.current.card
    val content = if (contentColor == Color.Unspecified) LocalContentColor.current else contentColor
    Column(modifier.fillMaxWidth().background(containerColor).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (navigationIcon != null) {
                Surface(shape = CircleShape, color = card, shadowElevation = 2.dp) { navigationIcon() }
            } else {
                Box(Modifier.padding(start = 8.dp))
            }
            Box(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                CompositionLocalProvider(LocalContentColor provides content) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)) { title() }
                }
            }
            Surface(shape = CircleShape, color = card, shadowElevation = 2.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        if (scrolled) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The usual case: a [title] on 2 lines at most, and « Retour » when [onBack] is given. */
@Composable
fun FamiliaTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FamiliaTopBar(
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = onBack?.let { back -> { BackButton(onClick = back) } },
        actions = actions,
        scrolled = scrolled,
    )
}
