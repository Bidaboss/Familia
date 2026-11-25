package fr.familia.app.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.ThemeChoice
import fr.familia.app.core.theme.ThemePhotoImage
import fr.familia.app.core.theme.ThemePhotos
import java.time.LocalDate

/**
 * Réglages > Thème (règle 15, fiche A3): a gallery of 3 columns, « Selon la saison » first. Each
 * picture is 3:4 with 14 dp corners; the chosen one has a 2 dp edge of its accent and a round check;
 * under it, its name and a dot of its accent. A tap applies the theme at once.
 */
@Composable
internal fun ThemeGallery(current: ThemeChoice, today: LocalDate, onChoose: (ThemeChoice) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeChoice.all.chunked(GALLERY_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { choice -> ThemeThumb(choice, choice == current, today, { onChoose(choice) }, Modifier.weight(1f)) }
                repeat(GALLERY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val GALLERY_COLUMNS = 3

@Composable
private fun ThemeThumb(choice: ThemeChoice, selected: Boolean, today: LocalDate, onClick: () -> Unit, modifier: Modifier) {
    val shown = choice.on(today)
    val accent = Color(shown.accent ?: 0xFF5A3A2E)
    val dark = Color(shown.dark ?: 0xFF2B2B2B)
    val name = if (choice == ThemeChoice.Season) stringResource(R.string.theme_season) else stringResource(shown.labelRes)
    val spoken = if (choice == ThemeChoice.Season) stringResource(R.string.theme_season_now, stringResource(shown.labelRes)) else null
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (spoken != null) stateDescription = spoken }
            .padding(bottom = 6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(shape)
                .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier),
        ) {
            ThemePhotoImage(shown, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(dark),
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(
                name,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** « Photo actuelle » (A3 §3): the photo shown now, useful when « Selon la saison » is on. */
@Composable
internal fun CurrentPhotoLine(current: ThemeChoice, today: LocalDate) {
    Text(
        stringResource(R.string.appearance_current_photo, stringResource(current.on(today).labelRes)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * « Crédits des photos » (règles 12 et 19): closed at first, a 56 dp line with its chevron; open, one
 * line per photo, « Photo de X sur Unsplash », its license, and its page in the browser.
 */
@Composable
internal fun PhotoCredits() {
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.appearance_credits_open else R.string.appearance_credits_closed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = state },
        ) {
            Text(stringResource(R.string.appearance_credits), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (open) {
            val uri = LocalUriHandler.current
            FamiliaThemeId.photos.forEach { theme ->
                val credit = ThemePhotos.credit(theme) ?: return@forEach
                val title = stringResource(theme.labelRes)
                val by = stringResource(R.string.appearance_credit_by, credit.author)
                val license = stringResource(R.string.appearance_credit_license)
                val opens = stringResource(R.string.appearance_credit_open, title)
                HorizontalDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { uri.openUri(credit.page) }
                        .semantics { contentDescription = "$title. $by. $license. $opens" }
                        .padding(vertical = 8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text("$by · $license", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
