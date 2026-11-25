package fr.familia.app.feature.gifts.surprise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import java.time.temporal.ChronoUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.householdGroups
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard

/** The people of one household, for the choice of « Préparer une surprise ». */
data class SurpriseGroup(val name: String?, val members: List<Member>)

sealed interface SurprisePickState {
    data object Loading : SurprisePickState

    data object Error : SurprisePickState

    data class Ready(val today: LocalDate, val next: Birthday?, val groups: List<SurpriseGroup>) : SurprisePickState
}

/**
 * Who a surprise is for (PRD rév. 287, récit 63): everyone but me, by household as in the Famille
 * tab, the next birthday first. Pure, for the tests.
 */
internal fun surprisePickState(family: Family, meId: String, today: LocalDate): SurprisePickState.Ready {
    val groups = householdGroups(family)
        .map { group -> SurpriseGroup(group.household?.name, group.members.filter { it.id != meId }) }
        .filter { it.members.isNotEmpty() }
    val next = upcomingBirthdays(family.members.filter { it.id != meId }, today).firstOrNull()
    return SurprisePickState.Ready(today, next, groups)
}

class SurprisePickViewModel(
    members: MemberRepository?,
    currentMemberId: String,
    today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {
    val state: StateFlow<SurprisePickState> = (
        members?.let { repo ->
            combine(repo.observeFamily(), today) { family, day -> surprisePickState(family, currentMemberId, day) as SurprisePickState }
        } ?: flowOf(SurprisePickState.Error)
        )
        .catch { e ->
            if (e is CancellationException) throw e
            emit(SurprisePickState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SurprisePickState.Loading)
}

/** Connects the choice of the person to its ViewModel; [onPick] goes on to the surprise itself. */
@Composable
fun SurprisePickRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit, onPick: (memberId: String) -> Unit) {
    val viewModel: SurprisePickViewModel = viewModel(
        factory = viewModelFactory { initializer { SurprisePickViewModel(container?.memberRepository, currentMember.id) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    SurprisePickScreen(state, onBack, onPick)
}

/** « Préparer une surprise » : pour qui ? Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurprisePickScreen(state: SurprisePickState, onBack: () -> Unit, onPick: (String) -> Unit) {
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.surprise_pick_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                SurprisePickState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SurprisePickState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.surprise_pick_title),
                    body = stringResource(R.string.surprise_pick_error),
                )
                is SurprisePickState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "question") {
                        Text(
                            stringResource(R.string.surprise_pick_question),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    state.next?.let { next ->
                        item(key = "next-title") { GroupTitle(stringResource(R.string.surprise_pick_next)) }
                        item(key = "next-${next.member.id}") {
                            PastilleRow(listOf(next.member), detail = { inDays(next.date, state.today) }, onPick = onPick)
                        }
                    }
                    state.groups.forEachIndexed { index, group ->
                        item(key = "group-$index") {
                            GroupTitle(group.name ?: stringResource(R.string.members_other_household))
                        }
                        // Pastilles of 64 dp, three per row (fiche C2).
                        items(group.members.chunked(PASTILLES_PER_ROW), key = { row -> "row-$index-${row.first().id}" }) { row ->
                            PastilleRow(row, detail = null, onPick = onPick)
                        }
                    }
                }
            }
        }
    }
}

private const val PASTILLES_PER_ROW = 3

/** « dans 12 jours » under the next birthday; « aujourd'hui » on the day. */
@Composable
private fun inDays(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days <= 0) dayShortLabel(date, today) else pluralStringResource(R.plurals.gifts_in_days, days, days)
}

@Composable
private fun PastilleRow(members: List<Member>, detail: (@Composable () -> String)?, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        members.forEach { member ->
            Pastille(member, detail?.invoke(), Modifier.weight(1f)) { onPick(member.id) }
        }
        repeat(PASTILLES_PER_ROW - members.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A round pastille with the initial or the photo, the first name under it. */
@Composable
private fun Pastille(member: Member, detail: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MemberAvatar(member, size = 64.dp)
        Text(
            member.firstName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
    )
}
