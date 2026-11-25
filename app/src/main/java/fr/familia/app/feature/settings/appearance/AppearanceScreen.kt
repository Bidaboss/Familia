package fr.familia.app.feature.settings.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.PhotoExtent
import fr.familia.app.core.theme.ThemeChoice
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
import java.time.LocalDate

/**
 * Ma photo › Apparence: the theme (récit 113, règles 12, 14 à 19, fiche A3): « Étendue de la photo »,
 * the gallery, « Selon la saison » first, the photo shown now, « Contraste fort » apart, and the
 * credits of the photos, closed; then the title font and the text font (DESIGN-45 and 46), each name
 * written in its own font. Every choice applies at once and stays on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    current: ThemeChoice,
    onChoose: (ThemeChoice) -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
    highContrast: Boolean = false,
    onHighContrast: (Boolean) -> Unit = {},
    photoExtent: PhotoExtent = PhotoExtent.Full,
    onChoosePhotoExtent: (PhotoExtent) -> Unit = {},
    titleFont: TitleFont = TitleFont.Round,
    onChooseTitleFont: (TitleFont) -> Unit = {},
    bodyFont: BodyFont = BodyFont.Normal,
    onChooseBodyFont: (BodyFont) -> Unit = {},
    icon: AppIcon = AppIcon.Default,
    onChooseIcon: (AppIcon) -> Unit = {},
    /** What plays at each opening (l'admin, 17/11): the welcome, the tree, or nothing. */
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
                    // « Étendue de la photo » before the gallery (l'admin, 25/11 10 h 42).
                    Text(stringResource(R.string.appearance_extent_title), style = MaterialTheme.typography.titleSmall)
                    ChoiceTiles(PhotoExtent.entries, columns = 2) { extent, tileModifier ->
                        ChoiceTile(
                            title = stringResource(if (extent == PhotoExtent.Full) R.string.appearance_extent_full else R.string.appearance_extent_top),
                            selected = extent == photoExtent,
                            onClick = { onChoosePhotoExtent(extent) },
                            modifier = tileModifier,
                        )
                    }
                    FormHint(stringResource(R.string.appearance_extent_help))
                    ThemeGallery(current, today, onChoose)
                    FormHint(stringResource(R.string.appearance_theme_help))
                    CurrentPhotoLine(current, today)
                    HighContrastRow(highContrast, onHighContrast)
                    PhotoCredits()
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

/** « Contraste fort » (règle 18): apart from the themes, plain and without photo. */
@Composable
private fun HighContrastRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.theme_contrast), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.theme_contrast_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

@FamiliaPreviews
@Composable
private fun AppearanceScreenPreview() {
    FamiliaTheme { Surface { AppearanceScreen(ThemeChoice.Season, {}, {}) } }
}
