package fr.familia.app.feature.discover

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import java.time.LocalDate

/** The module of the week on the Accueil; [onLater] is null in « Voir comme », where nothing is kept. */
class DiscoverHome(val module: DiscoverModule, val onTry: () -> Unit, val onLater: (() -> Unit)?, val forChild: Boolean = false) {
    @get:StringRes val titleRes: Int get() = if (forChild) module.childTitleRes else module.titleRes
    @get:StringRes val bodyRes: Int get() = if (forChild) module.childBodyRes else module.bodyRes
}

/**
 * The card « À découvrir » of this week for [currentMember], or null when there is none (AVIS-19).
 * « Essayer » and « Plus tard » hide it for good on this phone; in « Voir comme » nothing is written.
 */
@Composable
fun rememberDiscoverHome(currentMember: Member, onOpen: (DiscoverModule) -> Unit): DiscoverHome? {
    val context = LocalContext.current
    val store = remember(context) { DiscoverPreferences(context.applicationContext) }
    val viewingAs = LocalViewAs.current.viewed != null
    val flags = LocalFeatureFlags.current
    var dismissed by remember(currentMember.id) { mutableStateOf(store.load(currentMember.id)) }
    val module = Discover.moduleOfTheWeek(
        today = LocalDate.now(Discover.ZONE),
        isOn = flags::isOn,
        inTutorial = Discover.inTutorial(flags::isOn),
        dismissed = dismissed,
    ) ?: return null
    val hide: () -> Unit = {
        if (!viewingAs) {
            dismissed = dismissed + module.key
            store.save(currentMember.id, dismissed)
        }
    }
    return DiscoverHome(
        module = module,
        onTry = {
            hide()
            onOpen(module)
        },
        onLater = if (viewingAs) null else hide,
        forChild = currentMember.profile != MemberProfile.Adult,
    )
}

/** « À découvrir »: a title, one sentence, « Essayer » and « Plus tard ». It sends no notification. */
@Composable
fun DiscoverCard(discover: DiscoverHome, modifier: Modifier = Modifier) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        ClayCardTitle(stringResource(R.string.discover_title))
        Text(
            stringResource(discover.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(discover.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = discover.onTry, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.discover_try))
            }
            discover.onLater?.let { later ->
                TextButton(onClick = later, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.discover_later))
                }
            }
        }
    }
}

@Preview
@Composable
private fun DiscoverCardPreview() {
    FamiliaTheme {
        DiscoverCard(DiscoverHome(DiscoverModule.Recipes, onTry = {}, onLater = {}))
    }
}
