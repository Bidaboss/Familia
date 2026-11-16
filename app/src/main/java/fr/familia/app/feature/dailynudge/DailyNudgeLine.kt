package fr.familia.app.feature.dailynudge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags

/** The gesture of the day for the Accueil, behind Feature.DailyNudge; null when there is nothing to say. */
@Composable
fun rememberDailyNudge(container: AppContainer?, currentMember: Member): DailyNudge? {
    if (!LocalFeatureFlags.current.isOn(Feature.DailyNudge)) return null
    val viewModel: DailyNudgeViewModel = viewModel(
        key = "dailyNudge-${currentMember.id}",
        factory = viewModelFactory {
            initializer { DailyNudgeViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    return nudge
}

/**
 * One line of « À faire pour moi », without a card: the sentence, in the text color of the block
 * around it, and one link. [onOpenGoodNews]
 * opens the good news, where the reaction is one touch away.
 */
@Composable
fun DailyNudgeLine(nudge: DailyNudge, onOpenGoodNews: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (nudge) {
            is DailyNudge.ReactToPhoto -> {
                Text(
                    stringResource(R.string.daily_nudge_react, nudge.firstName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                ClayLink(stringResource(R.string.daily_nudge_react_action), onClick = { onOpenGoodNews(nudge.goodNewsId) })
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun DailyNudgeLinePreview() {
    FamiliaTheme {
        Surface { ClayCard { DailyNudgeLine(DailyNudge.ReactToPhoto("n1", "Camille"), {}) } }
    }
}
