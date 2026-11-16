package fr.familia.app.feature.polls.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.leaders
import fr.familia.app.core.model.notAnswered
import fr.familia.app.core.model.tally
import fr.familia.app.feature.polls.canManagePoll
import fr.familia.app.feature.polls.pollAnswerers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * One poll (PRD, récits 2 bis et 2 ter): my answer and my children's, the results with names, and
 * for its author « Terminer » and, for a date poll, « Choisir cette date », which adds the event to
 * the planning. Offline, writes land in the cache and Firestore sends them when the network is back.
 */
class PollDetailViewModel(
    private val polls: PollRepository?,
    private val members: MemberRepository?,
    private val events: EventRepository?,
    private val currentMemberId: String,
    private val pollId: String,
    private val now: () -> Instant = Instant::now,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private data class Action(val busy: Boolean = false, val error: PollActionError? = null)

    private val action = MutableStateFlow(Action())

    /** Answers being changed, by member; the saved vote shows until one is touched. */
    private val drafts = MutableStateFlow<Map<String, Draft>>(emptyMap())

    /** The members whose « Envoyer » was tapped with an answer missing. */
    private val incomplete = MutableStateFlow<Set<String>>(emptySet())

    private var latest: Loaded? = null

    private data class Loaded(val poll: Poll, val votes: List<PollVote>, val family: Family)

    val uiState: StateFlow<PollDetailUiState> = run {
        val pollRepo = polls
        val memberRepo = members
        if (pollRepo == null || memberRepo == null) {
            flowOf<PollDetailUiState>(PollDetailUiState.Missing)
        } else {
            combine(
                combine(pollRepo.observePoll(pollId), pollRepo.observeVotes(pollId), memberRepo.observeFamily()) { p, v, f -> Triple(p, v, f) },
                drafts,
                incomplete,
                action,
            ) { (poll, votes, family), edited, missing, act ->
                build(poll, votes, family, edited, missing, act)
            }.catch { emit(PollDetailUiState.Missing) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PollDetailUiState.Loading)

    private fun build(
        poll: Poll?,
        votes: List<PollVote>,
        family: Family,
        edited: Map<String, Draft>,
        missing: Set<String>,
        act: Action,
    ): PollDetailUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (poll == null || me == null) return PollDetailUiState.Missing
        latest = Loaded(poll, votes, family)
        val byId = family.members.associateBy { it.id }
        fun name(id: String) = byId[id]?.firstName
        // Votes of members no longer asked (or removed) are left out.
        val counted = votes.filter { it.memberId in poll.askedIds && it.memberId in byId }
        val isOpen = poll.isOpen(now())
        val tallies = tally(poll, counted)
        val leading = leaders(poll, counted)
        val results = tallies.mapIndexed { i, t ->
            OptionResult(
                index = i,
                yesNames = t.yes.mapNotNull(::name),
                maybeNames = t.maybe.mapNotNull(::name),
                isLeader = i in leading && poll.chosen == null,
                isChosen = poll.chosen == i,
            )
        }
        val answers = if (!isOpen) {
            emptyList()
        } else {
            pollAnswerers(poll, me, family).map { member ->
                val saved = counted.firstOrNull { it.memberId == member.id }?.let { draftOf(poll, it) }
                val draft = edited[member.id] ?: saved ?: emptyDraft(poll)
                AnswerCard(
                    memberId = member.id,
                    firstName = member.firstName,
                    isMe = member.id == me.id,
                    draft = draft,
                    isSaved = saved != null && saved == draft,
                    incomplete = member.id in missing && !isComplete(poll, draft),
                )
            }
        }
        val table = if (poll.kind == Poll.Kind.Date) {
            family.households.mapNotNull { household ->
                val rows = family.members
                    .filter { it.householdId == household.id && it.id in poll.askedIds }
                    .sortedWith(compareBy({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() }))
                    .map { member ->
                        val vote = counted.firstOrNull { it.memberId == member.id }
                        TableRow(member.firstName, poll.options.indices.map { vote?.answers?.getOrNull(it) })
                    }
                rows.takeIf { it.isNotEmpty() }?.let { TableGroup(household.name, it) }
            }
        } else {
            emptyList()
        }
        val canManage = canManagePoll(poll, me, family)
        return PollDetailUiState.Success(
            poll = poll,
            isOpen = isOpen,
            isMine = poll.authorId == me.id,
            authorName = name(poll.authorId) ?: poll.authorName,
            answers = answers,
            results = results,
            table = table,
            notAnsweredNames = notAnswered(poll, counted).mapNotNull(::name),
            canManage = canManage,
            canChooseDate = canManage && poll.kind == Poll.Kind.Date && poll.chosen == null,
            isBusy = act.busy,
            error = act.error,
        )
    }

    /** One date answer of [memberId]. */
    fun setAnswer(memberId: String, index: Int, answer: DateAnswer) = edit(memberId) { _, draft ->
        draft.copy(answers = draft.answers.mapIndexed { i, a -> if (i == index) answer else a })
    }

    /** Ticks or unticks a choice; with one answer only, the tap replaces the previous one. */
    fun toggleChoice(memberId: String, index: Int) = edit(memberId) { poll, draft ->
        val choices = when {
            index in draft.choices -> draft.choices - index
            poll.multiple -> draft.choices + index
            else -> setOf(index)
        }
        draft.copy(choices = choices)
    }

    private fun edit(memberId: String, change: (Poll, Draft) -> Draft) {
        val state = uiState.value as? PollDetailUiState.Success ?: return
        val card = state.answers.firstOrNull { it.memberId == memberId } ?: return
        drafts.update { it + (memberId to change(state.poll, card.draft)) }
    }

    /** Sends the answer of [memberId], once each date has one (or one choice is made). */
    fun send(memberId: String) {
        val state = uiState.value as? PollDetailUiState.Success ?: return
        val card = state.answers.firstOrNull { it.memberId == memberId } ?: return
        val repo = polls ?: return
        if (!isComplete(state.poll, card.draft)) {
            incomplete.update { it + memberId }
            return
        }
        incomplete.update { it - memberId }
        val vote = PollVote(
            memberId = memberId,
            answers = card.draft.answers.filterNotNull(),
            choices = card.draft.choices.sorted(),
            votedBy = currentMemberId,
        )
        write(onSuccess = { drafts.update { it - memberId } }) { repo.vote(pollId, vote) }
    }

    fun close() {
        val repo = polls ?: return
        write { repo.close(pollId) }
    }

    /**
     * « Choisir cette date »: the event is written first, by me, so the rules can check it, then the
     * poll is closed on it; the server signs up those who said yes. Both writes go in order, even offline.
     */
    fun chooseDate(index: Int, title: String) {
        val loaded = latest ?: return
        val pollRepo = polls ?: return
        val eventRepo = events ?: return
        val me = loaded.family.members.firstOrNull { it.id == currentMemberId } ?: return
        val (day, time) = Poll.dateOption(loaded.poll.options.getOrNull(index).orEmpty()) ?: return
        val eventTitle = title.trim().ifEmpty { loaded.poll.question.trim() }.take(EVENT_TITLE_MAX)
        val askedMembers = loaded.family.members.filter { it.id in loaded.poll.askedIds }
        // Only my household asked: the event is for my household; otherwise for the whole family.
        val target = if (askedMembers.isNotEmpty() && askedMembers.all { it.householdId == me.householdId }) {
            EventTarget.ForHousehold(me.householdId)
        } else {
            EventTarget.ForFamily
        }
        val event = Event(
            id = eventRepo.newEventId(),
            title = eventTitle,
            date = day,
            time = time,
            place = null,
            note = null,
            target = target,
            authorId = me.id,
            authorName = me.firstName,
        )
        write {
            eventRepo.saveEvent(event, isNew = true)
            pollRepo.chooseDate(pollId, index, event.id)
        }
    }

    fun delete() {
        val repo = polls ?: return
        write { repo.delete(pollId) }
    }

    fun dismissError() {
        action.update { it.copy(error = null) }
    }

    private fun write(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                onSuccess()
                Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Action(error = PollActionError.Failed)
            }
        }
    }

    companion object {
        /** The longest event title the rules accept. */
        const val EVENT_TITLE_MAX = 80
    }
}

internal fun emptyDraft(poll: Poll): Draft =
    if (poll.kind == Poll.Kind.Date) Draft(answers = poll.options.map { null }) else Draft()

internal fun draftOf(poll: Poll, vote: PollVote): Draft =
    if (poll.kind == Poll.Kind.Date) {
        Draft(answers = poll.options.indices.map { vote.answers.getOrNull(it) })
    } else {
        Draft(choices = vote.choices.toSet())
    }

internal fun isComplete(poll: Poll, draft: Draft): Boolean =
    if (poll.kind == Poll.Kind.Date) {
        draft.answers.size == poll.options.size && draft.answers.all { it != null }
    } else {
        draft.choices.isNotEmpty()
    }
