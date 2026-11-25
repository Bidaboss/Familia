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
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource

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
        // The next card peeks out on the right (retour du 17/11 : faire comprendre qu'il faut glisser).
        val peek = if (cards.size > 1) 40.dp else 0.dp
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(end = peek),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
        ) { page ->
            WhatsNewCardView(cards[page].card, child, onTry, Modifier.padding(vertical = 8.dp))
        }
        if (cards.size > 1) {
            val scope = rememberCoroutineScope()
            val page = pager.currentPage
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    enabled = page > 0,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.whats_new_previous))
                }
                val position = stringResource(R.string.whats_new_position, page + 1, cards.size)
                Row(
                    Modifier.weight(1f).semantics { contentDescription = position },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(cards.size) { index ->
                        Box(
                            Modifier
                                .size(if (index == page) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                    enabled = page < cards.size - 1,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.whats_new_next))
                }
            }
            if (page < cards.size - 1) {
                Text(
                    pluralStringResource(R.plurals.whats_new_swipe_hint, cards.size - page - 1, cards.size - page - 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    if (card.fixes) {
        FixesCardView(card, child, modifier)
        return
    }
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

/**
 * The corrections of a version (l'admin le 20/11): told apart from the new features, a small tool on
 * the side, a « Corrections » label, and the lines as a list, read from the left.
 */
@Composable
private fun FixesCardView(card: WhatsNewCard, child: Boolean, modifier: Modifier = Modifier) {
    ClayCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column {
                    Text(
                        stringResource(R.string.whats_new_fixes_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(card.titleFor(child), style = MaterialTheme.typography.titleMedium)
                }
            }
            card.bodyFor(child).lines().filter { it.isNotBlank() }.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
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
        WhatsNewCard(
            "corrections-200", "Ce qui a été corrigé",
            "• Cadeaux s'ouvre tout en haut\n• La photo d'une fiche s'ouvre en plein écran", fixes = true,
        ),
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
