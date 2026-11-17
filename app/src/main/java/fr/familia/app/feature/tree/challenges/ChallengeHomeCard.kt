package fr.familia.app.feature.tree.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.tree.TreeUiState
import fr.familia.app.feature.tree.TreeViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The fixed colours of DESIGN-16: pale yellow, black text, a black bar on a sand track. */
private object HomeChallengeColors {
    val Background = Color(0xFFFFF6DA)
    val Ink = Color(0xFF1E1B18)
    val Label = Color(0xFF6B5320)
    val Track = Color(0xFFF2E3B0)
}

/** The first challenge of the family: before it, the card says when it starts. */
private val FIRST_CHALLENGE_MONTH = YearMonth.of(2026, 12)

/** Whether the Accueil gives the card a place: a challenge this month, or the wait for the first one. */
fun showsChallenge(challenge: MonthChallenge?, month: YearMonth): Boolean = challenge != null || month < FIRST_CHALLENGE_MONTH

@Composable
private fun challengeHomeViewModel(container: AppContainer?, member: Member): TreeViewModel {
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    return viewModel(
        key = "challengeHome-$today-${member.householdId}",
        factory = viewModelFactory { initializer { TreeViewModel(container?.treeRepository, today, member.householdId) } },
    )
}

/** For the Accueil: true when the card has something to say. Always false while the tree is off. */
@Composable
fun rememberHasChallenge(container: AppContainer?, member: Member): Boolean {
    if (!LocalFeatureFlags.current.isOn(Feature.Tree)) return false
    val state by challengeHomeViewModel(container, member).uiState.collectAsStateWithLifecycle()
    return state?.let { showsChallenge(it.challenge, it.month) } == true
}

/**
 * « Défi du mois » on the Accueil R1 bis (§2.5, DESIGN-16). [wide] = false: the small card beside
 * the good news. [wide] = true: the big card, with the foyer that launched it and the black button
 * « Ajouter ma photo ». The card opens « Défis » ([onOpen]).
 */
@Composable
fun ChallengeHomeCard(
    container: AppContainer?,
    member: Member,
    wide: Boolean,
    onOpen: () -> Unit,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalFeatureFlags.current.isOn(Feature.Tree)) return
    val state by challengeHomeViewModel(container, member).uiState.collectAsStateWithLifecycle()
    val shown = state ?: return
    val members = container?.memberRepository
    val families = remember(members) { members?.observeFamily()?.map { it.households } ?: emptyFlow() }
    val households by families.collectAsStateWithLifecycle(initialValue = emptyList())
    val host = shown.challenge?.hostHouseholdId?.let { id -> households.firstOrNull { it.id == id }?.name }
    ChallengeHomeContent(shown, host, wide, onOpen, onAddPhoto, modifier)
}

/** Stateless: [host] is the name of the foyer that launched the challenge, if any. */
@Composable
fun ChallengeHomeContent(
    state: TreeUiState,
    host: String?,
    wide: Boolean,
    onOpen: () -> Unit,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val challenge = state.challenge
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(HomeChallengeColors.Background)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.challenge_home_open), onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.challenge_home_label).uppercase(),
            color = HomeChallengeColors.Label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (challenge == null) {
            Text(
                stringResource(if (state.month < FIRST_CHALLENGE_MONTH) R.string.challenges_none else R.string.challenges_none_month),
                color = HomeChallengeColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
            return@Column
        }
        Text(
            "${challenge.emoji} ${challenge.title}",
            color = HomeChallengeColors.Ink,
            style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            maxLines = if (wide) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (wide && host != null) {
            Text(stringResource(R.string.challenge_home_host, host), color = HomeChallengeColors.Label, style = MaterialTheme.typography.bodyMedium)
        }
        val spoken = stringResource(R.string.tree_challenge_progress, challenge.progress, challenge.target)
        LinearProgressIndicator(
            progress = { challenge.fraction },
            color = HomeChallengeColors.Ink,
            trackColor = HomeChallengeColors.Track,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).semantics { contentDescription = spoken },
        )
        Text(
            if (challenge.achieved) stringResource(R.string.tree_challenge_done) else spoken,
            color = HomeChallengeColors.Ink,
            style = MaterialTheme.typography.labelLarge,
        )
        if (wide && challenge.countsLinkedNews && !challenge.achieved) {
            Button(
                onClick = onAddPhoto,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HomeChallengeColors.Ink, contentColor = Color.White),
            ) { Text(stringResource(R.string.challenges_add_photo), fontWeight = FontWeight.Bold) }
        }
    }
}

@FamiliaPreviews
@Composable
private fun ChallengeHomeWidePreview() {
    val state = TreeUiState(
        fr.familia.app.core.model.FamilyTree(leaves = 30),
        MonthChallenge(YearMonth.of(2026, 12), "Une photo de votre sapin", "🎄", 4, 2, false, hostHouseholdId = "h1"),
        YearMonth.of(2026, 12),
    )
    FamiliaTheme { Surface { ChallengeHomeContent(state, "Foyer des Martin", wide = true, onOpen = {}, onAddPhoto = {}, modifier = Modifier.padding(16.dp)) } }
}

@FamiliaPreviews
@Composable
private fun ChallengeHomeBeforePreview() {
    val state = TreeUiState(fr.familia.app.core.model.FamilyTree(), null, YearMonth.of(2026, 11))
    FamiliaTheme { Surface { ChallengeHomeContent(state, null, wide = false, onOpen = {}, onAddPhoto = {}, modifier = Modifier.padding(16.dp)) } }
}

