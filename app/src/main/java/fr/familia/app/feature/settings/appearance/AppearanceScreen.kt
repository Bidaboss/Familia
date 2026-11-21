package fr.familia.app.feature.settings.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.TitleFont
import fr.familia.app.core.theme.sample
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.form.ChoiceTile
import fr.familia.app.core.ui.form.ChoiceTiles
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.welcome.OpeningStyle

/**
 * Ma photo › Apparence (DESIGN-7, revised by DESIGN-43): the 7 themes, each shown in its own
 * colors, then the title font and the text font (DESIGN-45 and 46), each name written in its own
 * font. Every choice applies at once and stays on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    current: FamiliaThemeId,
    onChoose: (FamiliaThemeId) -> Unit,
    onBack: () -> Unit,
    titleFont: TitleFont = TitleFont.Round,
    onChooseTitleFont: (TitleFont) -> Unit = {},
    bodyFont: BodyFont = BodyFont.Normal,
    onChooseBodyFont: (BodyFont) -> Unit = {},
    icon: AppIcon = AppIcon.Default,
    onChooseIcon: (AppIcon) -> Unit = {},
    /** What plays at each opening (Kévin, 17/11): the welcome, the tree, or nothing. */
    opening: OpeningStyle = OpeningStyle.Long,
    onChooseOpening: (OpeningStyle) -> Unit = {},
) {
    var iconChanged by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.themeBackground(),
        containerColor = Color.Transparent,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.appearance_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        // No button at the bottom: every choice applies at once, as before (lot-3-fiche-et-reglages.png).
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "theme") {
                FormTile(icon = Icons.Outlined.Palette, title = stringResource(R.string.appearance_theme_title)) {
                    ChoiceTiles(FamiliaThemeId.entries, columns = 2) { theme, tileModifier ->
                        ChoiceTile(
                            title = stringResource(theme.labelRes),
                            selected = theme == current,
                            onClick = { onChoose(theme) },
                            modifier = tileModifier,
                            top = { ThemeDots(theme) },
                        )
                    }
                    FormHint(stringResource(R.string.appearance_theme_help))
                }
            }
            item(key = "titleFont") {
                FontSection(
                    title = R.string.appearance_title_font_title,
                    help = R.string.appearance_title_font_help,
                    choices = TitleFont.entries.map { FontOption(it.labelRes, it.sample, it == titleFont) { onChooseTitleFont(it) } },
                    icon = Icons.Outlined.Title,
                )
            }
            item(key = "bodyFont") {
                FontSection(
                    title = R.string.appearance_body_font_title,
                    help = R.string.appearance_body_font_help,
                    choices = BodyFont.entries.map { FontOption(it.labelRes, it.sample, it == bodyFont) { onChooseBodyFont(it) } },
                    icon = Icons.Outlined.TextFields,
                )
            }
            item(key = "icon") {
                IconSection(icon, changed = iconChanged) { chosen ->
                    if (chosen != icon) {
                        onChooseIcon(chosen)
                        iconChanged = true
                    }
                }
            }
            item(key = "opening") {
                OpeningSection(opening, onChooseOpening)
            }
        }
    }
}

/** One font to choose: its name, written in [style], the font itself. */
internal class FontOption(@StringRes val labelRes: Int, val style: TextStyle, val selected: Boolean, val onChoose: () -> Unit)

/** « Police des titres » or « Police du texte »: a tile per font, its name in that font. */
@Composable
internal fun FontSection(
    @StringRes title: Int,
    @StringRes help: Int,
    choices: List<FontOption>,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.TextFields,
) {
    FormTile(icon = icon, title = stringResource(title)) {
        ChoiceTiles(choices, columns = if (choices.size > 2) 3 else 2) { choice, tileModifier ->
            ChoiceTile(
                title = stringResource(choice.labelRes),
                selected = choice.selected,
                onClick = choice.onChoose,
                modifier = tileModifier,
                titleStyle = choice.style,
            )
        }
        FormHint(stringResource(help))
    }
}

/** « Animation d'ouverture »: the welcome, the tree alone, or none, in 3 tiles. */
@Composable
internal fun OpeningSection(current: OpeningStyle, onChoose: (OpeningStyle) -> Unit) {
    FormTile(icon = Icons.Outlined.PlayCircle, title = stringResource(R.string.appearance_opening_title)) {
        ChoiceTiles(listOf(OpeningStyle.Short, OpeningStyle.Long, OpeningStyle.None), columns = 3) { style, tileModifier ->
            val (title, sub) = when (style) {
                OpeningStyle.Long -> R.string.appearance_opening_long_title to R.string.appearance_opening_long_sub
                OpeningStyle.Short -> R.string.appearance_opening_short_title to R.string.appearance_opening_short_sub
                OpeningStyle.None -> R.string.appearance_opening_none_title to null
            }
            ChoiceTile(
                title = stringResource(title),
                selected = style == current,
                onClick = { onChoose(style) },
                modifier = tileModifier,
                subtitle = sub?.let { stringResource(it) },
            )
        }
        FormHint(stringResource(R.string.appearance_opening_help))
    }
}

/**
 * « Icône de l'appli »: the 3 trees in 3 tiles. A change shows where to find the icon again, since
 * some launchers drop it from the home screen when it changes.
 */
@Composable
internal fun IconSection(current: AppIcon, changed: Boolean, onChoose: (AppIcon) -> Unit) {
    FormTile(icon = Icons.Outlined.Apps, title = stringResource(R.string.app_icon_title)) {
        ChoiceTiles(AppIcon.entries, columns = 3) { icon, tileModifier ->
            ChoiceTile(
                title = stringResource(icon.labelRes),
                selected = icon == current,
                onClick = { onChoose(icon) },
                modifier = tileModifier,
                top = { IconPicture(icon) },
            )
        }
        FormHint(stringResource(R.string.app_icon_help))
        if (changed) {
            Text(
                stringResource(R.string.app_icon_changed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The tree of [icon], as the launcher shows it. */
@Composable
private fun IconPicture(icon: AppIcon) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colorResource(icon.background)),
    ) {
        // The foreground is drawn on the 108 grid: its middle 72 is what the launcher shows.
        Image(
            painterResource(icon.foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(72.dp).align(Alignment.Center),
        )
    }
}

/** Three dots of [theme]: its background, its main color and its accent, drawn in that theme. */
@Composable
private fun ThemeDots(theme: FamiliaThemeId) {
    FamiliaTheme(darkTheme = isSystemInDarkTheme(), theme = theme) {
        val colors = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(colors.background, colors.primary, colors.secondary).forEach {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(it)
                        .border(1.dp, colors.outlineVariant, CircleShape),
                )
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun AppearanceScreenPreview() {
    FamiliaTheme { Surface { AppearanceScreen(FamiliaThemeId.Spain, {}, {}) } }
}
