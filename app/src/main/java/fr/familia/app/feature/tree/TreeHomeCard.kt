package fr.familia.app.feature.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.TreeSeason
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.home.HomeChip
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** The card of l'arbre de la famille (récit 35) on the Accueil, behind Feature.Tree. */
@Composable
fun TreeHomeCard(
    container: AppContainer?,
    modifier: Modifier = Modifier,
    currentMember: Member? = null,
    /** Opens the guide of the challenge of the foyer (récit 35 bis) for that month. */
    onPrepareChallenge: (YearMonth) -> Unit = {},
    /** « Participer au défi »: a Bonne nouvelle linked to the challenge of a foyer. */
    onJoinChallenge: () -> Unit = {},
) {
    val state by treeHomeViewModel(container, currentMember).uiState.collectAsStateWithLifecycle()
    state?.let { TreeHomeContent(it, modifier, onPrepareChallenge, onJoinChallenge) }
}

/**
 * The chip of the tree on the Accueil (DESIGN-11): « L'arbre · 74 feuilles », or « Défi du mois » while
 * it is the turn of my foyer to launch it. It opens [TreeHomeCard] under the row of chips.
 */
@Composable
fun TreeHomeChip(container: AppContainer?, currentMember: Member?, selected: Boolean, onToggle: () -> Unit) {
    val state by treeHomeViewModel(container, currentMember).uiState.collectAsStateWithLifecycle()
    val tree = state?.tree ?: return
    val label = if (state?.hostTurn != null) {
        stringResource(R.string.tree_chip_host)
    } else {
        stringResource(R.string.tree_chip, pluralStringResource(R.plurals.tree_leaves, tree.leaves, tree.leaves))
    }
    HomeChip(label = label, onClick = onToggle, selected = selected)
}

@Composable
private fun treeHomeViewModel(container: AppContainer?, currentMember: Member?): TreeViewModel {
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    return viewModel(
        key = "treeHome-$today-${currentMember?.householdId}",
        factory = viewModelFactory { initializer { TreeViewModel(container?.treeRepository, today, currentMember?.householdId) } },
    )
}

@Composable
fun TreeHomeContent(
    state: TreeUiState,
    modifier: Modifier = Modifier,
    onPrepareChallenge: (YearMonth) -> Unit = {},
    onJoinChallenge: () -> Unit = {},
) {
    val tree = state.tree
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tree_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val description = stringResource(R.string.tree_description, tree.level + 1)
            TreeDrawing(tree, TreeSeason.of(state.month), bloomed = tree.bloomed(state.month), modifier = Modifier.size(112.dp).semantics { contentDescription = description })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pluralStringResource(R.plurals.tree_leaves, tree.leaves, tree.leaves), style = MaterialTheme.typography.titleMedium)
                val next = tree.leavesToNextLevel
                Text(
                    if (next == null) stringResource(R.string.tree_top) else pluralStringResource(R.plurals.tree_next_level, next, next),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.challenge?.let { challenge ->
            ChallengeGauge(challenge)
            if (challenge.countsLinkedNews && !challenge.achieved) {
                // Tonal: the Accueil keeps one filled button at most (relecture of #111).
                FilledTonalButton(onClick = onJoinChallenge, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.tree_join_challenge))
                }
            }
        }
        state.hostTurn?.let { month -> HostTurn(month, onPrepareChallenge) }
    }
}

/** « C'est à vous de lancer le défi de janvier ! » for the foyer whose turn it is (récit 35 bis). */
@Composable
private fun HostTurn(month: YearMonth, onPrepare: (YearMonth) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tree_host_turn, ofMonth(month)), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.tree_host_turn_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = { onPrepare(month) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.tree_host_prepare))
        }
    }
}

/** The challenge of the month: its title and a gauge that moves by itself, never a name. */
@Composable
internal fun ChallengeGauge(challenge: MonthChallenge) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.tree_challenge, challenge.emoji, challenge.title),
            style = MaterialTheme.typography.titleMedium,
        )
        val gauge = stringResource(R.string.tree_challenge_progress, challenge.progress, challenge.target)
        LinearProgressIndicator(
            progress = { challenge.fraction },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).semantics { contentDescription = gauge },
        )
        Text(
            if (challenge.achieved) stringResource(R.string.tree_challenge_done) else gauge,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val trunk = Color(0xFF8A5A3B)
private val ground = Color(0xFFE8D9C8).copy(alpha = 0.6f)
private fun leafColor(season: TreeSeason) = when (season) {
    TreeSeason.Spring -> Color(0xFF8DB58A)
    TreeSeason.Summer -> Color(0xFF4F8F4A)
    TreeSeason.Autumn -> Color(0xFFE79A6E)
    TreeSeason.Winter -> Color(0xFF6F8F86)
}
private val blossom = Color(0xFFE87FA6)
private val highlight = Color.White.copy(alpha = 0.3f)
private val shadow = Color.Black.copy(alpha = 0.12f)

/** How many tufts each row of the crown has: levels 1 to 3 fill the inner row, then the outer one. */
internal fun tuftRows(level: Int): Pair<Int, Int> = (1 + level.coerceIn(0, 3)) to (2 * (level - 3).coerceAtLeast(0))

/**
 * A clay tree (DESIGN, 16/11): a flared trunk with two roots on a patch of ground, a big central
 * tuft and two rows of tufts that fill up with the levels, dressed for the season. Each tuft has a
 * light on its top left and a shadow below, without blur. It blooms the month the challenge is achieved.
 */
@Composable
fun TreeDrawing(tree: FamilyTree, season: TreeSeason, bloomed: Boolean, modifier: Modifier = Modifier) {
    val leaves = leafColor(season)
    val winter = season == TreeSeason.Winter
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val base = h * 0.92f
        drawOval(ground, topLeft = Offset(cx - w * 0.35f, base - h * 0.04f), size = Size(w * 0.7f, h * 0.08f))
        val grow = 0.45f + 0.09f * tree.level
        val trunkWidth = w * 0.12f
        val crownY = base - h * 0.42f * grow
        val trunkPath = Path().apply {
            moveTo(cx - trunkWidth / 2, crownY)
            lineTo(cx - trunkWidth / 2, base - trunkWidth * 0.7f)
            quadraticTo(cx - trunkWidth * 0.6f, base, cx - trunkWidth * 1.3f, base)
            lineTo(cx - trunkWidth * 0.35f, base)
            lineTo(cx, base - trunkWidth * 0.25f)
            lineTo(cx + trunkWidth * 0.35f, base)
            lineTo(cx + trunkWidth * 1.3f, base)
            quadraticTo(cx + trunkWidth * 0.6f, base, cx + trunkWidth / 2, base - trunkWidth * 0.7f)
            lineTo(cx + trunkWidth / 2, crownY)
            close()
        }
        drawPath(trunkPath, trunk)
        val radius = w * 0.15f * grow
        fun tuft(center: Offset, r: Float) {
            drawCircle(leaves, r, center)
            drawArc(shadow, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = center - Offset(r * 0.9f, r * 0.9f), size = Size(r * 1.8f, r * 1.8f))
            if (winter) drawArc(Color.White, startAngle = 205f, sweepAngle = 130f, useCenter = false, topLeft = center - Offset(r, r), size = Size(r * 2, r * 2))
            drawCircle(highlight, r * 0.55f, center + Offset(-r * 0.25f, -r * 0.25f))
            if (bloomed) {
                for (k in 0 until 3) {
                    val a = Math.toRadians(-90.0 + 120.0 * k + 30.0)
                    drawCircle(blossom, r * 0.15f, center + Offset((cos(a) * r * 0.55f).toFloat(), (sin(a) * r * 0.55f).toFloat()))
                }
            }
        }
        fun row(count: Int, spread: Float) {
            for (i in 0 until count) {
                val angle = Math.PI * (i + 0.5) / count
                tuft(Offset(cx + (cos(angle) * radius * spread).toFloat(), crownY - (sin(angle) * radius * spread * 0.8f).toFloat()), radius)
            }
        }
        val (inner, outer) = tuftRows(tree.level)
        row(outer, 1.8f)
        tuft(Offset(cx, crownY), radius * 1.3f)
        row(inner, 1.1f)
    }
}

internal fun previewTreeState(achieved: Boolean = false) = TreeUiState(
    tree = FamilyTree(leaves = 74, bloomedMonths = if (achieved) listOf("2026-12") else emptyList()),
    challenge = MonthChallenge(YearMonth.of(2026, 12), "20 photos de Noël", "🎄", 20, if (achieved) 20 else 7, achieved),
    month = YearMonth.of(2026, 12),
)

@FamiliaPreviews
@Composable
private fun TreeHomeCardPreview() {
    FamiliaTheme { Surface { TreeHomeContent(previewTreeState()) } }
}
