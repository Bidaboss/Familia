package fr.familia.app.feature.tree.challenges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.tree.TreeDrawing
import fr.familia.app.core.model.TreeSeason
import fr.familia.app.feature.tree.TreeUiState
import fr.familia.app.feature.tree.ofMonth
import fr.familia.app.feature.tree.previewTreeState
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionUiState
import fr.familia.app.feature.weeklyquestion.previewWeeklyQuestion
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun beforeFirstQuestion(): Boolean = LocalDate.now(ZoneId.of("Europe/Paris")).isBefore(FIRST_QUESTION)

/** The fixed colours of the maquette R7: a dark card for the challenge, the violet of S'organiser, the green of Se souvenir. */
private object ChallengeColors {
    val Ink = Color(0xFF1E1B18)
    val Yellow = Color(0xFFF2C14E)
    val Track = Color(0xFF4A453F)
    val Peach = Color(0xFFF9B99A)
    val Violet = Color(0xFF4A3F8C)
    val VioletPale = Color(0xFFF4F0FD)
    val Green = Color(0xFF1F5C3C)
    val GreenPale = Color(0xFFA8E0C2)
    val Bloom = Color(0xFFC9A2D6)
    val BloomDark = Color(0xFF4A1F5C)
}

/**
 * « Défis » (R1 bis, maquette R7): the challenge of the month, the question of Monday, then the
 * souvenirs the family won together: the tree and each challenge it made bloom. Never a ranking.
 * [weekly] is null while the question of the week is off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    tree: TreeUiState?,
    weekly: WeeklyQuestionUiState?,
    onBack: () -> Unit,
    onJoinChallenge: () -> Unit,
    onPrepareChallenge: (YearMonth) -> Unit,
    onOpenWeekly: () -> Unit,
    /** Before the first challenge, the fallback « Partager une bonne nouvelle en attendant »; null while Bonnes nouvelles is off. */
    onShareGoodNews: (() -> Unit)? = null,
    /** « Comment ça marche » under the cards (récit 65, switch challengeDetails); off, the screen of before. */
    details: Boolean = false,
    today: LocalDate = LocalDate.now(ZoneId.of("Europe/Paris")),
    /** The challenge of a month gone by, for its tile « Défi … réussi ». */
    loadChallenge: (YearMonth) -> Flow<MonthChallenge?> = { flowOf(null) },
) {
    var sheet by remember { mutableStateOf<ChallengeSheet?>(null) }
    var wonMonth by remember { mutableStateOf<YearMonth?>(null) }
    val explainNow: (MonthChallenge?) -> Unit = { challenge ->
        sheet = if (challenge == null) {
            challengePrinciple(today)
        } else {
            ChallengeSheet.Challenge(explain(challenge, today, tree?.hostNote, onShareGoodNews != null))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.challenges_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
        ) {
            if (tree != null) {
                item(key = "challenge") {
                    MonthChallengeCard(tree, onJoinChallenge, onPrepareChallenge, onShareGoodNews, onExplain = if (details) ({ explainNow(tree.challenge) }) else null)
                }
            }
            if (weekly != null && weekly !is WeeklyQuestionUiState.Loading) {
                item(key = "weekly") { WeeklyCard(weekly, onOpenWeekly, onExplain = if (details) ({ sheet = questionPrinciple(today) }) else null) }
            }
            if (tree != null) {
                item(key = "won-title") { Label(stringResource(R.string.challenges_won_title), ChallengeColors.Green) }
                item(key = "won") { WonTogether(tree.tree, tree.month, onOpenWon = if (details) ({ wonMonth = it }) else null) }
            }
        }
    }
    sheet?.let { open ->
        ChallengeHowSheet(open, onDismiss = { sheet = null }, onJoin = onJoinChallenge, onShareGoodNews = onShareGoodNews)
    }
    wonMonth?.let { month ->
        // The challenge of that month, read once: the explanation of what the family won.
        val won by remember(month) { loadChallenge(month) }.collectAsState(initial = null)
        won?.let { challenge ->
            val e = explain(challenge, today, hostNote = null, canShareGoodNews = false)
            ChallengeHowSheet(ChallengeSheet.Challenge(e), onDismiss = { wonMonth = null }, onJoin = {}, onShareGoodNews = null)
        }
    }
}

/** A whole card that opens something: TalkBack says it is a button, and what it does. */
private fun Modifier.openable(label: String, onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else clickable(onClickLabel = label, role = Role.Button, onClick = onClick)

@Composable
private fun Label(text: String, color: Color) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * The challenge of the month on a dark card: its title, a yellow gauge, and « Ajouter ma photo »
 * for a challenge of a foyer. Before the first one, the day it starts.
 */
@Composable
private fun MonthChallengeCard(
    state: TreeUiState,
    onJoin: () -> Unit,
    onPrepare: (YearMonth) -> Unit,
    onShareGoodNews: (() -> Unit)?,
    onExplain: (() -> Unit)? = null,
) {
    val challenge = state.challenge
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(ChallengeColors.Ink)
            .openable(stringResource(R.string.challenge_how_open), onExplain)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label(stringResource(R.string.challenges_month_label, ofMonth(state.month)), ChallengeColors.Peach)
        if (challenge == null) {
            val none = if (state.month < FIRST_CHALLENGE) R.string.challenges_none else R.string.challenges_none_month
            Text(stringResource(none), color = Color.White, style = MaterialTheme.typography.titleLarge)
            // Never a dead end: something to do while the challenge waits.
            if (onShareGoodNews != null) YellowButton(stringResource(R.string.empty_share_good_news), onShareGoodNews)
        } else {
            Text("${challenge.emoji} ${challenge.title}", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Gauge(challenge)
            if (challenge.countsLinkedNews && !challenge.achieved) {
                YellowButton(stringResource(R.string.challenges_add_photo), onJoin)
            }
        }
        state.hostTurn?.let { month ->
            Text(stringResource(R.string.tree_host_turn, ofMonth(month)), color = Color.White, style = MaterialTheme.typography.titleMedium)
            YellowButton(stringResource(R.string.tree_host_prepare)) { onPrepare(month) }
        }
    }
}

@Composable
private fun Gauge(challenge: MonthChallenge) {
    val spoken = stringResource(R.string.tree_challenge_progress, challenge.progress, challenge.target)
    LinearProgressIndicator(
        progress = { challenge.fraction },
        color = ChallengeColors.Yellow,
        trackColor = ChallengeColors.Track,
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).semantics { contentDescription = spoken },
    )
    Text(
        if (challenge.achieved) stringResource(R.string.tree_challenge_done) else stringResource(R.string.challenges_progress, spoken),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun YellowButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ChallengeColors.Yellow, contentColor = ChallengeColors.Ink),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** The question of Monday: the question, how many answered, never who; « Tu réponds pour les voir ». */
@Composable
private fun WeeklyCard(state: WeeklyQuestionUiState, onOpen: () -> Unit, onExplain: (() -> Unit)? = null) {
    // The whole card opens the question, or its answers once I answered (récit 65); without a
    // question, « Comment ça marche » under the switch.
    val answered = (state as? WeeklyQuestionUiState.Success)?.myAnswer != null
    val open = if (state is WeeklyQuestionUiState.Success) onOpen else onExplain
    val label = when {
        state !is WeeklyQuestionUiState.Success -> stringResource(R.string.challenge_how_open)
        answered -> stringResource(R.string.weekly_open_answers)
        else -> stringResource(R.string.weekly_open)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White)
            .openable(label, open)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(stringResource(R.string.challenges_weekly_label), ChallengeColors.Violet)
        when (state) {
            is WeeklyQuestionUiState.Success -> {
                Text(state.question.text, color = ChallengeColors.Ink, style = MaterialTheme.typography.titleLarge)
                val count = pluralStringResource(R.plurals.weekly_answer_count, state.question.answerCount, state.question.answerCount)
                Text(
                    if (state.myAnswer == null) stringResource(R.string.challenges_weekly_answer_to_see, count) else count,
                    color = ChallengeColors.Ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = ChallengeColors.VioletPale, contentColor = ChallengeColors.Ink),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Text(
                        stringResource(if (state.myAnswer == null) R.string.weekly_answer else R.string.weekly_see_answers),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            else -> Text(stringResource(if (beforeFirstQuestion()) R.string.challenges_weekly_none else R.string.challenges_weekly_none_week), color = ChallengeColors.Ink, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * What the family won together: the tree and its leaves, each month it bloomed, then « À venir ».
 * Shared by everyone: nothing here is one person's.
 */
@Composable
private fun WonTogether(tree: FamilyTree, month: YearMonth, onOpenWon: ((YearMonth) -> Unit)? = null) {
    val bloomed = tree.bloomedMonths.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.sortedDescending()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "tree") {
            Tile(pluralStringResource(R.plurals.tree_leaves, tree.leaves, tree.leaves)) {
                TreeDrawing(tree, TreeSeason.of(month), bloomed = tree.bloomed(month), modifier = Modifier.size(56.dp))
            }
        }
        items(bloomed, key = { it.toString() }) { won ->
            Tile(stringResource(R.string.challenges_won_month, ofMonth(won)), onOpen = onOpenWon?.let { open -> { open(won) } }) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(ChallengeColors.Bloom), contentAlignment = Alignment.Center) {
                    Text("🌸", fontSize = 24.sp)
                }
            }
        }
        item(key = "next") {
            Tile(stringResource(R.string.challenges_won_next)) {
                Canvas(Modifier.size(52.dp)) {
                    drawCircle(
                        ChallengeColors.Ink.copy(alpha = 0.35f),
                        style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, onOpen: (() -> Unit)? = null, image: @Composable () -> Unit) {
    val openLabel = stringResource(R.string.challenge_how_open)
    Column(
        Modifier
            .width(96.dp)
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .clearAndSetSemantics {
                contentDescription = label
                if (onOpen != null) {
                    role = Role.Button
                    onClick(label = openLabel) { onOpen(); true }
                }
            }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        image()
        Text(label, color = ChallengeColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@FamiliaPreviews
@Composable
private fun ChallengesScreenPreview() {
    FamiliaTheme {
        Surface {
            ChallengesScreen(
                tree = previewTreeState().let { it.copy(tree = it.tree.copy(bloomedMonths = listOf("2026-11"))) },
                weekly = previewWeeklyQuestion(answered = false),
                onBack = {},
                onJoinChallenge = {},
                onPrepareChallenge = {},
                onOpenWeekly = {},
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun ChallengesScreenEmptyPreview() {
    FamiliaTheme {
        Surface {
            ChallengesScreen(
                tree = TreeUiState(FamilyTree(), challenge = null, month = YearMonth.of(2026, 11)),
                weekly = WeeklyQuestionUiState.None,
                onBack = {},
                onJoinChallenge = {},
                onPrepareChallenge = {},
                onOpenWeekly = {},
            )
        }
    }
}
