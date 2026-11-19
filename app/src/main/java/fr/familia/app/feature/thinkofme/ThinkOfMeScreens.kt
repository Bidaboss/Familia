package fr.familia.app.feature.thinkofme

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.householdGroups
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.MemberAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * « Pensez à moi » out of the member's page (PRODUIT-135, 18/11): the « + » of the Accueil opens
 * « Penser à quelqu'un », a choice of the person, then their « Pensez à moi » on a screen of its own.
 * The notification of the day opens that screen too.
 */

/** The people of one household, for the choice of « Penser à quelqu'un ». */
data class ThinkOfMeGroup(val name: String?, val members: List<Member>)

sealed interface ThinkOfMePickState {
    data object Loading : ThinkOfMePickState

    data object Error : ThinkOfMePickState

    /** [me] first, to note my own moments; then everyone else by household, as in the Famille tab. */
    data class Ready(val me: Member?, val groups: List<ThinkOfMeGroup>) : ThinkOfMePickState
}

/** Pure, for the tests. */
internal fun thinkOfMePickState(family: Family, meId: String): ThinkOfMePickState.Ready {
    val groups = householdGroups(family)
        .map { group -> ThinkOfMeGroup(group.household?.name, group.members.filter { it.id != meId }) }
        .filter { it.members.isNotEmpty() }
    return ThinkOfMePickState.Ready(family.members.firstOrNull { it.id == meId }, groups)
}

class ThinkOfMePickViewModel(members: MemberRepository?, currentMemberId: String) : ViewModel() {
    val state: StateFlow<ThinkOfMePickState> = (
        members?.observeFamily()?.map { thinkOfMePickState(it, currentMemberId) as ThinkOfMePickState }
            ?: flowOf(ThinkOfMePickState.Error)
        )
        .catch { e ->
            if (e is CancellationException) throw e
            emit(ThinkOfMePickState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThinkOfMePickState.Loading)
}

@Composable
fun ThinkOfMePickRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit, onPick: (memberId: String) -> Unit) {
    val viewModel: ThinkOfMePickViewModel = viewModel(
        factory = viewModelFactory { initializer { ThinkOfMePickViewModel(container?.memberRepository, currentMember.id) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThinkOfMePickScreen(state, onBack, onPick)
}

/** « Penser à quelqu'un » : « Moi » en tête, puis la famille par foyer. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkOfMePickScreen(state: ThinkOfMePickState, onBack: () -> Unit, onPick: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.think_of_me_pick_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ThinkOfMePickState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ThinkOfMePickState.Error -> EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.think_of_me_pick_title),
                    body = stringResource(R.string.think_of_me_pick_error),
                )
                is ThinkOfMePickState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "intro") {
                        Text(stringResource(R.string.think_of_me_pick_intro), style = MaterialTheme.typography.bodyLarge)
                    }
                    state.me?.let { me ->
                        item(key = "me") {
                            PersonRow(me, stringResource(R.string.think_of_me_pick_me), stringResource(R.string.think_of_me_pick_me_detail)) { onPick(me.id) }
                        }
                    }
                    state.groups.forEachIndexed { index, group ->
                        item(key = "group-$index") {
                            Text(
                                group.name ?: stringResource(R.string.members_other_household),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                            )
                        }
                        items(group.members, key = { "member-$index-${it.id}" }) { member ->
                            PersonRow(member, member.firstName, null) { onPick(member.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(member: Member, name: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The member to think of, read from the family; null while it loads, or if they left. */
private class ThinkOfMePersonViewModel(members: MemberRepository?, memberId: String) : ViewModel() {
    val person: StateFlow<Result<Member?>?> = (
        members?.observeFamily()?.map { family -> Result.success(family.members.firstOrNull { it.id == memberId }) }
            ?: flowOf(Result.success(null))
        )
        .catch { e ->
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** « Pensez à moi » of [memberId] on a screen of its own: the section that was on the member's page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkOfMeRoute(container: AppContainer?, currentMember: Member, memberId: String, onBack: () -> Unit) {
    val viewModel: ThinkOfMePersonViewModel = viewModel(
        key = "think-of-me-person-$memberId",
        factory = viewModelFactory { initializer { ThinkOfMePersonViewModel(container?.memberRepository, memberId) } },
    )
    val read by viewModel.person.collectAsStateWithLifecycle()
    val person = read?.getOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when {
                        person == null -> stringResource(R.string.think_of_me_title)
                        person.id == currentMember.id -> stringResource(R.string.think_of_me_title)
                        else -> stringResource(R.string.think_of_me_for, person.firstName)
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                read == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                person == null -> EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.think_of_me_title),
                    body = stringResource(R.string.think_of_me_pick_error),
                )
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ThinkOfMeSection(container, currentMember, person, showTitle = false)
                }
            }
        }
    }
}
