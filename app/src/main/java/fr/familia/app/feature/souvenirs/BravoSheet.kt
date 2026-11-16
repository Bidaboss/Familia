package fr.familia.app.feature.souvenirs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * The « Bravo » sheet ([DESIGN], SouvenirMedal): shown once on this phone for a new souvenir, on
 * one's own page. The medal at 160 dp, its name, the day, and the choice to show it to the family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BravoSheet(kind: SouvenirKind, souvenir: Souvenir, onShow: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        BravoContent(kind, souvenir, onShow, onDismiss)
    }
}

@Composable
internal fun BravoContent(kind: SouvenirKind, souvenir: Souvenir, onShow: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.souvenir_bravo_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        SouvenirMedal(kind, souvenir.earnedAt, size = 160.dp)
        Text(stringResource(kind.title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(if (souvenir.shown) R.string.souvenir_bravo_shown else R.string.souvenir_bravo_private),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!souvenir.shown) {
            Button(onClick = onShow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.souvenirs_show))
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.souvenir_bravo_close))
        }
    }
}

@FamiliaPreviews
@Composable
private fun BravoPreview() {
    FamiliaTheme {
        Surface { BravoContent(SouvenirKind.FirstGoodNews, previewSouvenirs().last(), {}, {}) }
    }
}
