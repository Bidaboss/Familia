package fr.familia.app.feature.settings.appearance

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.dayHeaderColor
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import androidx.compose.foundation.isSystemInDarkTheme
import fr.familia.app.core.theme.ClayTint

/**
 * Famille › Réglages › Apparence (DESIGN-7): the 9 themes, each shown in its own colors and fonts.
 * The choice applies at once and stays on this phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    current: FamiliaThemeId,
    onChoose: (FamiliaThemeId) -> Unit,
    onBack: () -> Unit,
    icon: AppIcon = AppIcon.Default,
    onChooseIcon: (AppIcon) -> Unit = {},
) {
    var iconChanged by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().selectableGroup(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.appearance_theme_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.appearance_theme_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(FamiliaThemeId.entries, key = { it.key }) { theme ->
                ThemeChoice(theme, selected = theme == current) { onChoose(theme) }
            }
            item(key = "icon") {
                IconSection(icon, changed = iconChanged) { chosen ->
                    if (chosen != icon) {
                        onChooseIcon(chosen)
                        iconChanged = true
                    }
                }
            }
        }
    }
}

/**
 * « Icône de l'appli »: the 3 trees side by side. A change shows where to find the icon again,
 * since some launchers drop it from the home screen when it changes.
 */
@Composable
internal fun IconSection(current: AppIcon, changed: Boolean, onChoose: (AppIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.app_icon_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.app_icon_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                IconChoice(icon, selected = icon == current, modifier = Modifier.weight(1f)) { onChoose(icon) }
            }
        }
        if (changed) {
            Text(
                stringResource(R.string.app_icon_changed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun IconChoice(icon: AppIcon, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colorResource(icon.background)),
        ) {
            // The foreground is drawn on the 108 grid: its middle 72 is what the launcher shows.
            Image(
                painterResource(icon.foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(108.dp).align(Alignment.Center),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(stringResource(icon.labelRes), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** One theme: a radio button, its name, and a small planning card drawn in that theme. */
@Composable
private fun ThemeChoice(theme: FamiliaThemeId, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(theme.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(48.dp))
        FamiliaTheme(darkTheme = isSystemInDarkTheme(), theme = theme) {
            ThemeSample(name, Modifier.weight(1f))
        }
    }
}

/** What the theme looks like: its background, a day header and a card with its colors. */
@Composable
private fun ThemeSample(name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.large).themeBackground().padding(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = dayHeaderTextStyle(), color = dayHeaderColor())
            ClayCard(modifier = Modifier.fillMaxWidth(), tint = ClayTint.Family) {
                Text(
                    stringResource(R.string.appearance_sample_event),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, FamiliaClay.colors.household)
                        .forEach { Box(Modifier.size(20.dp).clip(CircleShape).background(it)) }
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun AppearanceScreenPreview() {
    FamiliaTheme { Surface { AppearanceScreen(FamiliaThemeId.Spain, {}, {}) } }
}
