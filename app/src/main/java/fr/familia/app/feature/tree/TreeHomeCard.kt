package fr.familia.app.feature.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.TreeSeason
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** The card of l'arbre de la famille (récit 35) on the Accueil, behind Feature.Tree. */
@Composable
fun TreeHomeCard(container: AppContainer?, modifier: Modifier = Modifier) {
    val month = YearMonth.now(ZoneId.of("Europe/Paris"))
    val viewModel: TreeViewModel = viewModel(
        key = "treeHome-$month",
        factory = viewModelFactory { initializer { TreeViewModel(container?.treeRepository, month) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state?.let { TreeHomeContent(it, modifier) }
}

@Composable
fun TreeHomeContent(state: TreeUiState, modifier: Modifier = Modifier) {
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
                Text(stringResource(R.string.tree_how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.challenge?.let { ChallengeGauge(it) }
    }
}

/** The challenge of the month: its title and a gauge that moves by itself, never a name. */
@Composable
private fun ChallengeGauge(challenge: MonthChallenge) {
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
private fun leafColor(season: TreeSeason) = when (season) {
    TreeSeason.Spring -> Color(0xFF8DB58A)
    TreeSeason.Summer -> Color(0xFF4F8F4A)
    TreeSeason.Autumn -> Color(0xFFE79A6E)
    TreeSeason.Winter -> Color(0xFFB9CBD6)
}
private val blossom = Color(0xFFF2A7C3)

/**
 * A clay tree: a trunk and round tufts, one more tuft for each level, dressed for the season. It
 * blooms (pink dots) the month the challenge is achieved. To be refined with [DESIGN].
 */
@Composable
fun TreeDrawing(tree: FamilyTree, season: TreeSeason, bloomed: Boolean, modifier: Modifier = Modifier) {
    val leaves = leafColor(season)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val trunkWidth = w * 0.12f
        val grow = 0.45f + 0.09f * tree.level
        val trunkHeight = h * 0.45f * grow
        drawRoundRect(
            color = trunk,
            topLeft = Offset(w / 2 - trunkWidth / 2, h - trunkHeight),
            size = Size(trunkWidth, trunkHeight),
            cornerRadius = CornerRadius(trunkWidth / 2),
        )
        val crownY = h - trunkHeight
        val radius = w * 0.16f * grow
        val tufts = 1 + tree.level
        drawCircle(leaves, radius * 1.3f, Offset(w / 2, crownY))
        for (i in 0 until tufts) {
            val angle = Math.PI * (i + 0.5) / tufts
            val center = Offset(w / 2 + (cos(angle) * radius * 1.4f).toFloat(), crownY - (sin(angle) * radius * 0.9f).toFloat())
            drawCircle(leaves, radius, center)
            if (bloomed) drawCircle(blossom, radius * 0.25f, center)
        }
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
