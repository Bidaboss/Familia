package fr.familia.app.feature.helprequests.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.helprequests.declineHelp
import fr.familia.app.feature.helprequests.detail.HelpActionError
import fr.familia.app.feature.helprequests.needsMe
import fr.familia.app.feature.helprequests.takeHelp
import fr.familia.app.feature.helprequests.whenLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One request that waits for me, with who asks, for the card. */
@Immutable
data class HelpRequestTodo(val request: HelpRequest, val author: Member?, val helpedName: String?, val today: LocalDate)

/**
 * The coups de main that wait for me, for « À faire » of the Accueil (présentation R1 bis, §2 point
 * 4): [todos], the soonest first, and what « J'y vais » and « Non » do. A request someone took
 * leaves the list at once. [error] is the last refusal (already taken, offline).
 */
@Immutable
class HelpRequestTodos(
    val todos: List<HelpRequestTodo>,
    val error: HelpActionError?,
    val onTake: (HelpRequest) -> Unit,
    val onDecline: (HelpRequest) -> Unit,
    val onDismissError: () -> Unit,
)

/** The requests among [requests] that wait for [meId], the soonest first. */
internal fun todosFor(requests: List<HelpRequest>, family: Family, meId: String, today: LocalDate): List<HelpRequestTodo> {
    val me = family.members.firstOrNull { it.id == meId } ?: return emptyList()
    return requests
        .filter { needsMe(it, me, today) }
        .sortedWith(compareBy({ it.date }, { it.startTime != null }, { it.startTime }, { it.title.lowercase() }))
        .map { request ->
            HelpRequestTodo(
                request = request,
                author = family.members.firstOrNull { it.id == request.authorId },
                helpedName = request.forMemberId.takeIf { it != request.authorId }?.let { id -> family.members.firstOrNull { it.id == id }?.firstName },
                today = today,
            )
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HelpRequestTodosViewModel(
    private val requests: HelpRequestRepository?,
    private val members: MemberRepository?,
    private val meId: String,
    today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {
    private var family: Family? = null

    val todos: StateFlow<List<HelpRequestTodo>> = run {
        val requestRepo = requests
        val memberRepo = members
        if (requestRepo == null || memberRepo == null) {
            flowOf(emptyList())
        } else {
            combine(requestRepo.observeMine(meId), memberRepo.observeFamily(), today) { list, f, day ->
                family = f
                todosFor(list, f, meId, day)
            }.catch { emit(emptyList()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<HelpActionError?>(null)
    val error: StateFlow<HelpActionError?> = _error

    fun take(request: HelpRequest) = act { repo, me -> takeHelp(repo, request, me) }

    fun decline(request: HelpRequest) = act { repo, me -> declineHelp(repo, request, me) }

    fun dismissError() {
        _error.value = null
    }

    private fun act(block: suspend (HelpRequestRepository, Member) -> HelpActionError?) {
        val repo = requests ?: return
        val me = family?.members?.firstOrNull { it.id == meId } ?: return
        viewModelScope.launch { _error.value = block(repo, me) }
    }
}

/** The coups de main that wait for [member], to place in « À faire » (the Accueil owns where). */
@Composable
fun rememberHelpRequestTodos(container: AppContainer?, member: Member): HelpRequestTodos {
    val viewModel: HelpRequestTodosViewModel = viewModel(
        key = "helpTodos-${member.id}",
        factory = viewModelFactory {
            initializer { HelpRequestTodosViewModel(container?.helpRequestRepository, container?.memberRepository, member.id) }
        },
    )
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    return HelpRequestTodos(todos, error, viewModel::take, viewModel::decline, viewModel::dismissError)
}

/**
 * One coup de main that waits for me, with its buttons on the card (R1 bis): « J'y vais » and
 * « Non » on the Accueil, « Je m'en occupe » and « Pas dispo » at the top of Entraide ([large]).
 * Buttons are 48 dp, as inside every card. A tap elsewhere on the card opens the request.
 */
@Composable
fun HelpRequestTodoCard(
    todo: HelpRequestTodo,
    onTake: () -> Unit,
    onDecline: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val request = todo.request
    val tint = ClayTint.Household
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        tint = tint,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.helprequest_open),
        emphasis = large,
    ) {
        Text(
            request.title,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
        Text(whenLabel(request, todo.today), style = MaterialTheme.typography.bodyLarge, color = FamiliaClay.colors.onTintVariant(tint))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            todo.author?.let { MemberAvatar(it, size = 36.dp) }
            Text(
                todo.helpedName?.let { stringResource(R.string.helprequest_todo_for, request.authorName, it) } ?: request.authorName,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onTake, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(if (large) R.string.helprequest_todo_take_large else R.string.helprequest_todo_take))
            }
            OutlinedButton(onClick = onDecline, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (large) R.string.helprequest_todo_decline_large else R.string.helprequest_todo_decline))
            }
        }
    }
}

/** The French words of a refusal from a card, or null. */
@Composable
fun helpActionErrorText(error: HelpActionError?): String? = when (error) {
    null -> null
    is HelpActionError.AlreadyTaken -> error.volunteerName?.let { stringResource(R.string.helprequest_todo_already_taken_by, it) }
        ?: stringResource(R.string.helprequest_todo_already_taken)
    HelpActionError.NeedsNetwork -> stringResource(R.string.helprequest_todo_needs_network)
    HelpActionError.Failed -> stringResource(R.string.helprequest_todo_failed)
}
