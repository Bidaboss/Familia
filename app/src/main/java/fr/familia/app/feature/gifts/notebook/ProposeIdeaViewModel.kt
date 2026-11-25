package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.ideasListId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the member is choosing: the list, and whether they take the gift themselves. */
private data class ProposeChoice(
    val listId: String? = null,
    val takeIt: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val doneListId: String? = null,
)

sealed interface ProposeIdeaUiState {
    data object Loading : ProposeIdeaUiState

    data object Unavailable : ProposeIdeaUiState

    data class Choosing(
        val entry: NotebookEntry,
        /** The lists of the person where the idea stays hidden from them, « Mes envies » first. */
        val lists: List<GiftList>,
        val selectedListId: String?,
        val takeIt: Boolean,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        /** « Idées de la famille » nobody started yet (récit 9 ter): the idea creates it. */
        val newList: GiftList? = null,
    ) : ProposeIdeaUiState

    /** The idea is now an item of [listId]. */
    data class Done(val listId: String) : ProposeIdeaUiState
}

/**
 * « Proposer à la famille » (PRD, récit 9 bis): a private idea for a member becomes an item of one of
 * their lists, hidden from them, so that the family can take it or share it. The idea leaves the
 * notebook; if the member takes the gift, it comes back as a reservation.
 */
class ProposeIdeaViewModel(
    private val notebook: NotebookRepository?,
    private val gifts: GiftRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val entryId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val choice = MutableStateFlow(ProposeChoice())

    private data class Context(val entry: NotebookEntry, val me: Member, val family: Family)

    /** The member as the family knows them, once loaded: the author of the item. */
    private var author: Member? = null

    /** The item of a first attempt, by list: a new attempt writes it again, never a second one. */
    private val itemIds = mutableMapOf<String, String>()

    /** The lists where the item is already written: a new attempt only retries the rest. */
    private val written = mutableSetOf<String>()

    /** The « Idées de la famille » this screen created: never created twice. */
    private val created = mutableSetOf<String>()

    /** Starts [block] now, up to its first suspension, so that Firestore queues the write in order. */
    private fun write(block: suspend () -> Unit): Deferred<Unit> = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) { block() }

    private val context = flow {
        val repo = notebook ?: return@flow emit(null)
        val memberRepo = members ?: return@flow emit(null)
        val family = memberRepo.observeFamily().first()
        val me = family.members.firstOrNull { it.id == currentMemberId }
        val entry = repo.observeEntries(currentMemberId).first().firstOrNull { it.id == entryId }
        author = me
        emit(if (me != null && entry != null && canPropose(entry)) Context(entry, me, family) else null)
    }.catch { emit(null) }

    val uiState: StateFlow<ProposeIdeaUiState> = combine(
        context,
        gifts?.observeVisibleLists(currentMemberId)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        choice,
    ) { ctx, visible, pick ->
        when {
            pick.doneListId != null -> ProposeIdeaUiState.Done(pick.doneListId)
            ctx == null || gifts == null -> ProposeIdeaUiState.Unavailable
            else -> {
                val personId = ctx.entry.personId!!
                val ideas = visible.firstOrNull { it.id == ideasListId(personId) }
                val target = ideas ?: GiftList(
                    ideasListId(personId), GiftListKind.Ideas, "Idées de la famille", null, null, personId, emptyList(), ctx.me.id, ctx.me.firstName,
                )
                ProposeIdeaUiState.Choosing(
                    entry = ctx.entry,
                    lists = listOf(target),
                    selectedListId = target.id,
                    takeIt = pick.takeIt,
                    isSaving = pick.isSaving,
                    saveFailed = pick.saveFailed,
                    newList = target.takeIf { ideas == null },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProposeIdeaUiState.Loading)

    fun selectList(listId: String) {
        choice.value = choice.value.copy(listId = listId, saveFailed = false)
    }

    fun setTakeIt(takeIt: Boolean) {
        choice.value = choice.value.copy(takeIt = takeIt, saveFailed = false)
    }

    fun propose() {
        val state = uiState.value as? ProposeIdeaUiState.Choosing ?: return
        val giftRepo = gifts ?: return
        val repo = notebook ?: return
        val listId = state.selectedListId ?: return
        if (state.isSaving) return
        val entry = state.entry
        val me = author ?: return
        choice.value = choice.value.copy(listId = listId, isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val itemId = itemIds.getOrPut(listId) { giftRepo.newItemId(listId) }
            // The private note stays in the notebook: it may say where the gift is hidden.
            val item = GiftItem(
                id = itemId, title = entry.title, url = entry.url, price = entry.price, note = null,
                imageUrl = entry.imageUrl, whereToFind = entry.whereToFind,
                authorId = me.id, authorName = me.firstName, visibleToBeneficiary = false,
            )
            // The item and the reservation are queued at once, in this order, so that they reach the
            // server even offline: waiting for one before the next would hold the other back.
            val writes = buildList {
                val newList = state.newList?.takeIf { it.id == listId }
                if (newList != null && listId !in created) add(write { giftRepo.saveList(newList, isNew = true); created += listId })
                if (listId !in written) {
                    add(write { giftRepo.saveItem(listId, item, isNew = true); written += listId })
                }
                if (state.takeIt) add(write { giftRepo.claim(listId, GiftClaim(itemId, ClaimType.Solo, me.id, me.firstName)) })
            }
            val ok = try {
                // Still pending after the delay: queued offline, Firestore sends it later.
                withTimeoutOrNull(writeTimeoutMillis) { writes.awaitAll() }
                // The idea leaves the notebook only once the item is written or queued: a refused
                // item keeps it.
                write { repo.deleteIdea(currentMemberId, entry.id) }.let { withTimeoutOrNull(writeTimeoutMillis) { it.await() } }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            choice.value = if (ok) choice.value.copy(isSaving = false, doneListId = listId) else choice.value.copy(isSaving = false, saveFailed = true)
        }
    }
}

/** Only a private idea, still an idea, for a member of the family. */
internal fun canPropose(entry: NotebookEntry): Boolean =
    entry.source == NotebookSource.Idea && entry.status == GiftStatus.Idea && entry.personId != null
