package fr.familia.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.ClaySpacing

/** What the « + » of the Accueil adds (R1 bis, §2.8); an option is null while its module is off. */
class HomeAddOptions(
    /** « Dire où je suis » (Rejoignez-moi), while the places module is on. */
    val onWhereAmI: (() -> Unit)? = null,
    val onEvent: (() -> Unit)? = null,
    val onPhoto: (() -> Unit)? = null,
    val onGoodNews: (() -> Unit)? = null,
    val onWish: (() -> Unit)? = null,
    val onHelp: (() -> Unit)? = null,
)

/**
 * The sheet of « + », two by two (DESIGN, 17/11): Dire où je suis, Un événement, Une photo, Une bonne
 * nouvelle, Une envie, Un coup de main. With an odd count, the last one takes the whole width. Then
 * Annuler. Each choice closes the sheet first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAddSheet(options: HomeAddOptions, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ClaySpacing.screen)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.home_r1_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val choices = listOfNotNull(
                options.onWhereAmI?.let { Choice(Icons.Outlined.Place, R.string.home_r1_add_where, it) },
                options.onEvent?.let { Choice(Icons.Outlined.Event, R.string.home_r1_add_event, it) },
                options.onPhoto?.let { Choice(Icons.Outlined.AddAPhoto, R.string.home_r1_add_photo, it) },
                options.onGoodNews?.let { Choice(Icons.Outlined.Celebration, R.string.home_r1_add_good_news, it) },
                options.onWish?.let { Choice(Icons.Outlined.CardGiftcard, R.string.home_r1_add_wish, it) },
                options.onHelp?.let { Choice(Icons.Outlined.VolunteerActivism, R.string.home_r1_add_help, it) },
            )
            choices.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { choice -> ChoiceButton(choice, onDismiss, Modifier.weight(1f)) }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private class Choice(val icon: ImageVector, @StringRes val label: Int, val onClick: () -> Unit)

@Composable
private fun ChoiceButton(choice: Choice, onDismiss: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = {
            onDismiss()
            choice.onClick()
        },
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Icon(choice.icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(choice.label))
    }
}
