package fr.familia.app.feature.tree.challenges

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.tree.TreeViewModel
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Connects « Défis » to the tree and to the question of the week, each behind its own switch. */
@Composable
fun ChallengesRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    onJoinChallenge: () -> Unit,
    onPrepareChallenge: (YearMonth) -> Unit,
    onOpenWeekly: () -> Unit,
) {
    val flags = LocalFeatureFlags.current
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    val treeModel: TreeViewModel = viewModel(
        key = "challenges-tree-$today-${currentMember.householdId}",
        factory = viewModelFactory { initializer { TreeViewModel(container?.treeRepository, today, currentMember.householdId) } },
    )
    val weeklyModel: WeeklyQuestionViewModel = viewModel(
        key = "challenges-weekly",
        factory = viewModelFactory {
            initializer { WeeklyQuestionViewModel(container?.weeklyQuestionRepository, container?.memberRepository, currentMember.id, null) }
        },
    )
    val tree by treeModel.uiState.collectAsStateWithLifecycle()
    val weekly by weeklyModel.uiState.collectAsStateWithLifecycle()
    ChallengesScreen(
        tree = tree.takeIf { flags.isOn(Feature.Tree) },
        weekly = weekly.takeIf { flags.isOn(Feature.WeeklyQuestion) },
        onBack = onBack,
        onJoinChallenge = onJoinChallenge,
        onPrepareChallenge = onPrepareChallenge,
        onOpenWeekly = onOpenWeekly,
    )
}
