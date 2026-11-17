package fr.familia.app.feature.gooddeals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Member
import fr.familia.app.feature.gooddeals.list.GoodDealsScreen
import fr.familia.app.feature.gooddeals.list.GoodDealsViewModel
import java.time.LocalDate

/** The day in Paris, kept up to date past midnight. */
@Composable
private fun parisToday(): LocalDate {
    val flow = remember { currentDayFlow({ LocalDate.now(PARIS) }) }
    val today by flow.collectAsStateWithLifecycle(LocalDate.now(PARIS))
    return today
}

/** The « Bons plans » of Entraide (récit 45), shown under its tab. */
@Composable
fun GoodDealsRoute(
    container: AppContainer?,
    currentMember: Member,
    onPublish: (() -> Unit)?,
    onOpenDeal: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: GoodDealsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GoodDealsViewModel(container?.goodDealRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoodDealsScreen(state, parisToday(), onPublish, onOpenDeal, viewModel::retry, modifier)
}
