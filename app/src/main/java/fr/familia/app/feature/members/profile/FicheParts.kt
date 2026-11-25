package fr.familia.app.feature.members.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.photoCard

/*
 * The bricks of the member page lying on the photo of the theme (brief Famille-recettes, F6 à F8): a
 * title of section on the photo, then one card of frosted glass holding rows, each with its emoji in
 * a cream round. Without a photo theme, the same cards on the theme's background.
 */

/** The ink of the texts inside a glass card: dark on the cream, whatever the theme. */
private val GlassInk = Color(0xFF1E1B18)
private val GlassInkSoft = Color(0xFF55493F)

/** The cream round behind the emoji of a row (F6: #F3EADB). */
private val EmojiRound = Color(0xFFF3EADB)

/** True when the page lies on a photo. */
@Composable
@ReadOnlyComposable
internal fun onPhoto(): Boolean = LocalPhotoTheme.current != null

/** A card of the page: glass on the photo, the clay card elsewhere. */
@Composable
internal fun Modifier.ficheCard(radius: Dp = 24.dp): Modifier {
    if (onPhoto()) return photoCard(radius)
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(LocalClayColors.current.card).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

/** The ink of a card's texts. */
@Composable
@ReadOnlyComposable
internal fun ficheInk(): Color = if (onPhoto()) GlassInk else MaterialTheme.colorScheme.onSurface

@Composable
@ReadOnlyComposable
internal fun ficheInkSoft(): Color = if (onPhoto()) GlassInkSoft else MaterialTheme.colorScheme.onSurfaceVariant

/** A card of the page with its content in the card's ink. */
@Composable
internal fun FicheCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides ficheInk()) {
        Column(modifier.fillMaxWidth().ficheCard().padding(vertical = 6.dp), content = content)
    }
}

/** A text lying on the photo itself: white with a shadow (dark on the cream of « Le haut seulement »). */
@Composable
internal fun onPhotoStyle(base: TextStyle): TextStyle =
    if (onPhoto()) base.merge(TextStyle(color = PhotoSectionText, shadow = PhotoSectionShadow)) else base

/**
 * A section: its title on the photo (« À savoir »), then its card. [onEdit] puts a pencil next to
 * the title, for those who may change it.
 */
@Composable
internal fun FicheSection(
    title: String,
    onEdit: (() -> Unit)? = null,
    editLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = onPhotoStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = editLabel,
                        tint = if (onPhoto()) PhotoSectionText else LocalContentColor.current,
                    )
                }
            }
        }
        FicheCard(content = content)
    }
}

/**
 * A row of a card (F6): the emoji in its cream round, a title and a line under it. The whole row is
 * one touch when [onClick] is given, with a « › » at the end unless [trailing] says otherwise.
 */
@Composable
internal fun FicheRow(
    emoji: String,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FicheEmoji(emoji)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ficheInkSoft()) }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Text("›", style = MaterialTheme.typography.titleLarge, color = ficheInkSoft(), modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** The emoji of a row in its cream round, 48 dp; TalkBack reads the title instead. */
@Composable
internal fun FicheEmoji(emoji: String, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(EmojiRound).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

/** The thin line between two rows of a card. */
@Composable
internal fun FicheDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = ficheInk().copy(alpha = 0.12f))
}

/** A small sentence at the bottom of a card (« Visible de toute la famille. »). */
@Composable
internal fun FicheSmall(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = ficheInkSoft(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The rows of a card, a line between each. */
@Composable
internal fun FicheRows(rows: List<@Composable () -> Unit>) {
    rows.forEachIndexed { index, row ->
        if (index > 0) FicheDivider()
        row()
    }
}
