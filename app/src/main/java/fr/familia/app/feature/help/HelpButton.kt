package fr.familia.app.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/** Opens the help page. Provided by FamiliaApp, so that each screen only says which help it shows. */
val LocalOpenHelp = compositionLocalOf<() -> Unit> { {} }

/** The « ? » of a top bar: opens a sheet with [help] and a link to the whole help page. */
@Composable
fun HelpButton(help: ScreenHelp) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(56.dp)) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.help_button))
    }
    HelpSheetWhen(open, help) { open = false }
}

/** « En savoir plus », after a short sentence: opens the same sheet as [HelpButton]. */
@Composable
fun HelpLink(help: ScreenHelp, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier.heightIn(min = 56.dp)) {
        Text(stringResource(R.string.help_more))
    }
    HelpSheetWhen(open, help) { open = false }
}

@Composable
private fun HelpSheetWhen(open: Boolean, help: ScreenHelp, close: () -> Unit) {
    if (open) {
        val openHelp = LocalOpenHelp.current
        HelpSheet(help, onDismiss = close, onOpenAllHelp = {
            close()
            openHelp()
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(help: ScreenHelp, onDismiss: () -> Unit, onOpenAllHelp: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HelpSheetContent(help, onOpenAllHelp)
    }
}

/** The sheet's content, apart so that it can be captured in screenshots. */
@Composable
fun HelpSheetContent(help: ScreenHelp, onOpenAllHelp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(help.titleRes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(help.bodyRes), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onOpenAllHelp, modifier = Modifier.heightIn(min = 56.dp).padding(bottom = 8.dp)) {
            Text(stringResource(R.string.help_all))
        }
    }
}
