package fr.familia.app.feature.gooddeals

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/**
 * The Entraide tab with its « Bons plans » (PRD, récit 45), « On se prête » (récit 51) and « À qui
 * le tour ? » (récit 58): the coups de main first, then each module switched on. With all off, only
 * the coups de main.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntraideTabs(
    goodDealsOn: Boolean,
    help: @Composable () -> Unit,
    goodDeals: @Composable () -> Unit,
    /** Each new value turns to the Bons plans (a touched notification). */
    showGoodDeals: Int = 0,
    lendingOn: Boolean = false,
    lending: @Composable () -> Unit = {},
    turnsOn: Boolean = false,
    turns: @Composable () -> Unit = {},
    /** Each new value turns to « À qui le tour ? » (a touched notification). */
    showTurns: Int = 0,
    /** A sub-tab asked once from « Tout Familia » (PRODUIT-118); [onOpened] spends it. */
    open: EntraideTab? = null,
    onOpened: () -> Unit = {},
) {
    val tabs = buildList {
        add(EntraideTab.Help)
        if (goodDealsOn) add(EntraideTab.GoodDeals)
        if (lendingOn) add(EntraideTab.Lending)
        if (turnsOn) add(EntraideTab.Turns)
    }
    if (tabs.size == 1) {
        if (open != null) LaunchedEffect(open) { onOpened() }
        help()
        return
    }
    var selected by rememberSaveable { mutableStateOf(if (showGoodDeals > 0 && goodDealsOn) EntraideTab.GoodDeals else EntraideTab.Help) }
    LaunchedEffect(showGoodDeals) {
        if (showGoodDeals > 0 && goodDealsOn) selected = EntraideTab.GoodDeals
    }
    LaunchedEffect(showTurns) {
        if (showTurns > 0 && turnsOn) selected = EntraideTab.Turns
    }
    LaunchedEffect(open) {
        if (open != null) {
            if (open in tabs) selected = open
            onOpened()
        }
    }
    // A module switched off while its tab was open: back to the coups de main.
    val shown = selected.takeIf { it in tabs } ?: EntraideTab.Help
    Column(Modifier.fillMaxSize()) {
        val tabItems: @Composable () -> Unit = {
            tabs.forEach { tab ->
                Tab(
                    selected = shown == tab,
                    onClick = { selected = tab },
                    text = { Text(stringResource(tab.label)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        // Past three tabs the labels no longer fit a phone's width: the row scrolls.
        if (tabs.size > 3) {
            PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(shown), modifier = Modifier.fillMaxWidth(), edgePadding = 0.dp) { tabItems() }
        } else {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(shown), modifier = Modifier.fillMaxWidth()) { tabItems() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (shown) {
                EntraideTab.Help -> help()
                EntraideTab.GoodDeals -> goodDeals()
                EntraideTab.Lending -> lending()
                EntraideTab.Turns -> turns()
            }
        }
    }
}

enum class EntraideTab(@StringRes val label: Int) {
    Help(R.string.entraide_tab_help),
    GoodDeals(R.string.entraide_tab_good_deals),
    Lending(R.string.entraide_tab_lending),
    Turns(R.string.entraide_tab_turns),
}
