package fr.familia.app.core.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.discover.DiscoverModule
import fr.familia.app.feature.discover.DiscoverPreferences

/**
 * Opens « Quoi de neuf ? » once per opening, when there are cards for [currentMember] (PRD, récit
 * 6 septies). [freshInstall]: this opening showed the tutorial. Nothing shows and nothing is kept
 * in « Voir comme ». Once a card is seen, « À découvrir » skips its module (AVIS-19).
 */
@Composable
fun WhatsNewHost(
    appConfig: AppConfig?,
    currentMember: Member,
    installedVersion: Long,
    freshInstall: Boolean,
    onOpen: (WhatsNewTarget) -> Unit,
) {
    if (LocalViewAs.current.viewed != null) return
    val context = LocalContext.current
    val decider = remember(context) { WhatsNewDecider(WhatsNewPreferences(context.applicationContext)) }
    val discover = remember(context) { DiscoverPreferences(context.applicationContext) }
    // Decided once per opening: a module switched on meanwhile shows at the next one.
    var decided by rememberSaveable(currentMember.id) { mutableStateOf(false) }
    var cards by remember(currentMember.id) { mutableStateOf(emptyList<ShownCard>()) }
    val child = WhatsNew.isChild(currentMember.profile)
    LaunchedEffect(appConfig != null, currentMember.id) {
        // Noted at once, even before config/app is read, so a new install closed early stays one.
        if (freshInstall) decider.noteFreshInstall(currentMember.id)
        if (appConfig == null || decided) return@LaunchedEffect
        decided = true
        cards = decider.cardsToShow(currentMember.id, appConfig.whatsNew, installedVersion, child) { appConfig.features[it] == true }
    }
    if (cards.isEmpty()) return
    val close = { later: Boolean ->
        if (later) decider.postpone(currentMember.id, cards) else decider.markSeen(currentMember.id, cards)
        val modules = DiscoverModule.entries.filter { m -> cards.any { it.card.feature == m.feature.key } }.map { it.key }
        if (modules.isNotEmpty()) discover.save(currentMember.id, discover.load(currentMember.id) + modules)
        cards = emptyList()
    }
    WhatsNewSheet(cards, child, onGo = { close(false) }, onLater = { close(true) }, onTry = { target ->
        close(false)
        onOpen(target)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(cards: List<ShownCard>, child: Boolean, onGo: () -> Unit, onLater: () -> Unit, onTry: (WhatsNewTarget) -> Unit) {
    ModalBottomSheet(onDismissRequest = onLater) {
        WhatsNewSheetContent(cards, child, onGo, onLater, onTry)
    }
}

/** The content of the sheet, apart for previews and screenshots: one card per page, swiped. */
@Composable
fun WhatsNewSheetContent(cards: List<ShownCard>, child: Boolean, onGo: () -> Unit, onLater: () -> Unit, onTry: (WhatsNewTarget) -> Unit) {
    val pager = rememberPagerState { cards.size }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.whats_new_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalPager(pager, pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
            WhatsNewCardView(cards[page].card, child, onTry, Modifier.padding(vertical = 8.dp))
        }
        if (cards.size > 1) {
            Text(
                stringResource(R.string.whats_new_position, pager.currentPage + 1, cards.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onGo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.whats_new_go))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.whats_new_later))
        }
    }
}

/** One card: the Argile illustration of its screen, the title, one sentence and « Essayer ». */
@Composable
fun WhatsNewCardView(card: WhatsNewCard, child: Boolean, onTry: ((WhatsNewTarget) -> Unit)?, modifier: Modifier = Modifier) {
    val target = card.target
    ClayCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        target?.icon ?: WhatsNewTarget.defaultIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Text(card.titleFor(child), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(card.bodyFor(child), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (target != null && onTry != null) {
                OutlinedButton(onClick = { onTry(target) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.whats_new_try))
                }
            }
        }
    }
}

/** Fictional cards, for previews and screenshots. */
internal val previewWhatsNew = WhatsNewRelease(
    id = "exemple",
    versionCode = 200,
    versionName = "1.200",
    cards = listOf(
        WhatsNewCard("carnet", "Le carnet de recettes", "Garde les plats de la famille et ajoute la photo du tien.", open = "recipes"),
        WhatsNewCard(
            "voyages", "Les voyages", "Prépare le prochain voyage et dis « bien arrivé » en un geste.",
            childBody = "Regarde où part la famille et ajoute tes photos.", open = "trips",
        ),
        WhatsNewCard("theme", "Plus de couleurs", "Choisis la couleur de l'appli dans les réglages."),
    ),
)

@FamiliaPreviews
@Composable
private fun WhatsNewSheetPreview() {
    FamiliaTheme {
        Surface {
            WhatsNewSheetContent(previewWhatsNew.cards.map { ShownCard(it, previewWhatsNew) }, child = false, onGo = {}, onLater = {}, onTry = {})
        }
    }
}
