package fr.familia.app.feature.gooddeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.feature.goodnews.postedLabel
import fr.familia.app.feature.home.HomeChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

/** How long « Nouveau bon plan » stays on the Accueil (PRD, récit 45). */
internal val NEW_GOOD_DEAL_DAYS: Duration = Duration.ofDays(2)

/** The newest bon plan of someone else from the last 2 days and still running, or null. */
internal fun newGoodDealFor(deals: List<GoodDeal>, meId: String, now: Instant): GoodDeal? =
    deals
        .filter {
            it.kind == GoodDealKind.Deal && it.authorId != meId && it.isActive(now) &&
                it.createdAt != null && it.createdAt <= now && now < it.createdAt.plus(NEW_GOOD_DEAL_DAYS)
        }
        .maxByOrNull { it.createdAt!! }

/** The full photo of [deal] for the Accueil card, or null when it has none. */
internal fun goodDealHomePhoto(deal: GoodDeal): String? = deal.photo?.takeIf { it.isNotBlank() }?.let(SinglePhoto::photoPath)

class GoodDealHomeViewModel(repository: GoodDealRepository?, meId: String, clock: () -> Instant = Instant::now) : ViewModel() {
    val deal: StateFlow<GoodDeal?> = (repository?.observeAll() ?: flowOf(emptyList()))
        .map { newGoodDealFor(it, meId, clock()) }
        .catch { e -> if (e is CancellationException) throw e else emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * « Nouveau bon plan : … », which opens the bon plan itself, only while it is new. The Accueil
 * places it among its chips, just before « Dire où je suis » (agreed with the Accueil).
 */
@Composable
fun GoodDealHomeChip(container: AppContainer?, meId: String, onOpenGoodDeal: (String) -> Unit) {
    val viewModel: GoodDealHomeViewModel = viewModel(
        key = "goodDealHome-$meId",
        factory = viewModelFactory { initializer { GoodDealHomeViewModel(container?.goodDealRepository, meId) } },
    )
    val deal by viewModel.deal.collectAsStateWithLifecycle()
    val shown = deal ?: return
    HomeChip(stringResource(R.string.home_new_good_deal, shown.title), onClick = { onOpenGoodDeal(shown.id) })
}

/**
 * The new bon plan as a card on the Accueil (retour d'essai, 18/11): « BON PLAN », its photo
 * in a wide thumbnail when it has one, the title, then who posted it and when. Touching the
 * card opens the bon plan. Same rule as the chip: only while it is new.
 */
@Composable
fun GoodDealHomeCard(container: AppContainer?, meId: String, onOpenGoodDeal: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: GoodDealHomeViewModel = viewModel(
        key = "goodDealHome-$meId",
        factory = viewModelFactory { initializer { GoodDealHomeViewModel(container?.goodDealRepository, meId) } },
    )
    val deal by viewModel.deal.collectAsStateWithLifecycle()
    val shown = deal ?: return
    val now = remember(shown.id) { Instant.now() }
    GoodDealHomeCardContent(shown, now, onOpen = { onOpenGoodDeal(shown.id) }, modifier = modifier)
}

@Composable
internal fun GoodDealHomeCardContent(deal: GoodDeal, now: Instant, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val tint = ClayTint.Plain
    val photo = goodDealHomePhoto(deal)
    val openLabel = stringResource(R.string.home_good_deal_open)
    ClayCard(modifier = modifier.fillMaxWidth().clickable(onClickLabel = openLabel, onClick = onOpen), tint = tint) {
        SectionLabel(stringResource(R.string.home_good_deal_label))
        if (photo != null) {
            StorageImage(
                path = photo,
                contentDescription = stringResource(R.string.gooddeal_photo_label),
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(deal.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.home_good_deal_byline, deal.authorName, postedLabel(deal.createdAt, now)),
            style = MaterialTheme.typography.labelLarge,
            color = FamiliaClay.colors.onTintVariant(tint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (photo == null && deal.text.isNotBlank()) {
            Text(deal.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@FamiliaPreviews
@Composable
private fun GoodDealHomeCardPreview() {
    val now = Instant.parse("2026-11-18T13:00:00Z")
    FamiliaTheme {
        GoodDealHomeCardContent(
            GoodDeal(
                id = "d1",
                kind = GoodDealKind.Deal,
                title = "Pommes à -30 % au marché",
                text = "Jusqu'à samedi, au stand du fond.",
                endsAt = now.plus(Duration.ofDays(3)),
                authorId = "camille",
                authorName = "Camille",
                createdAt = now.minus(Duration.ofHours(2)),
            ),
            now,
            onOpen = {},
        )
    }
}
