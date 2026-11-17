package fr.familia.app.feature.gooddeals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/**
 * The Entraide tab with its « Bons plans » (PRD, récit 45): « Coups de main » and « Bons plans »
 * side by side. With the module off, only the coups de main, as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntraideTabs(
    goodDealsOn: Boolean,
    help: @Composable () -> Unit,
    goodDeals: @Composable () -> Unit,
    startOnGoodDeals: Boolean = false,
) {
    if (!goodDealsOn) {
        help()
        return
    }
    var selected by rememberSaveable { mutableIntStateOf(if (startOnGoodDeals) 1 else 0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected, modifier = Modifier.fillMaxWidth()) {
            listOf(R.string.entraide_tab_help, R.string.entraide_tab_good_deals).forEachIndexed { index, label ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(stringResource(label)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selected == 0) help() else goodDeals()
        }
    }
}
