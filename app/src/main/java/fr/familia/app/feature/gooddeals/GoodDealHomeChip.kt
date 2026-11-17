package fr.familia.app.feature.gooddeals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
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
