package fr.familia.app.feature.whoisit

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.WhoIsItRepository
import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItPost
import fr.familia.app.core.model.parseWhoIsItYear
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Year

/*
 * « Qui est-ce ? » (PRD, récit 43): an old photo, the family guesses who and the year, and the
 * answer shows once one has guessed. No score, nobody is « right ». Behind Feature.WhoIsIt.
 */

/** What this phone remembers for a member: the photos set aside, and those already guessed here. */
interface WhoIsItMemory {
    fun load(memberId: String, kind: String): Set<String>
    fun save(memberId: String, kind: String, ids: Set<String>)
}

class WhoIsItPreferences(context: Context) : WhoIsItMemory {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)
    override fun load(memberId: String, kind: String): Set<String> = prefs.getStringSet("whoIsIt-$kind-$memberId", emptySet()).orEmpty().toSet()
    override fun save(memberId: String, kind: String, ids: Set<String>) = prefs.edit { putStringSet("whoIsIt-$kind-$memberId", ids) }
}

const val DISMISSED = "dismissed"
const val GUESSED = "guessed"

// --- One photo: guess, then the answer ------------------------------------------------------------

sealed interface WhoIsItPostUiState {
    data object Loading : WhoIsItPostUiState
    data object Missing : WhoIsItPostUiState

    data class Ready(
        val post: WhoIsItPost,
        /** Shown once guessed (or to the author); null before. */
        val answer: WhoIsItAnswer?,
        val guesses: List<WhoIsItGuess>,
        val isAuthor: Boolean,
        val canDelete: Boolean,
        /** False in « Voir comme »: one reads, one never guesses. */
        val canGuess: Boolean,
        val who: String = "",
        val year: String = "",
        val yearInvalid: Boolean = false,
        val sending: Boolean = false,
        val failed: Boolean = false,
    ) : WhoIsItPostUiState {
        val revealed: Boolean get() = answer != null || isAuthor
    }
}

class WhoIsItPostViewModel(
    private val repo: WhoIsItRepository?,
    private val memberId: String,
    private val memberName: String,
    private val postId: String,
    private val isOwner: Boolean,
    private val canWrite: Boolean = true,
    private val memory: WhoIsItMemory? = null,
    private val thisYear: Int = Year.now().value,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val draft = MutableStateFlow(Draft())

    private data class Draft(val who: String = "", val year: String = "", val yearInvalid: Boolean = false, val sending: Boolean = false, val failed: Boolean = false)

    val uiState: StateFlow<WhoIsItPostUiState> = if (repo == null) {
        MutableStateFlow(WhoIsItPostUiState.Missing)
    } else {
        combine(repo.observePost(postId), repo.observeAnswer(postId), repo.observeGuesses(postId), draft) { post, answer, guesses, d ->
            if (post == null) return@combine WhoIsItPostUiState.Missing
            val isAuthor = post.authorId == memberId
            WhoIsItPostUiState.Ready(
                post = post,
                answer = answer,
                guesses = guesses,
                isAuthor = isAuthor,
                canDelete = canWrite && (isAuthor || isOwner),
                canGuess = canWrite && !isAuthor,
                who = d.who,
                year = d.year,
                yearInvalid = d.yearInvalid,
                sending = d.sending,
                failed = d.failed,
            )
        }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(WhoIsItPostUiState.Missing)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoIsItPostUiState.Loading)
    }

    fun setWho(text: String) = draft.update { it.copy(who = text.take(WhoIsItPost.MAX_WHO), failed = false) }
    fun setYear(text: String) = draft.update { it.copy(year = text.filter(Char::isDigit).take(4), yearInvalid = false, failed = false) }

    /** « Deviner »: the name and maybe the year. */
    fun guess() {
        val d = draft.value
        val year = parseWhoIsItYear(d.year, thisYear).getOrElse {
            draft.update { it.copy(yearInvalid = true) }
            return
        }
        send(d.who.trim(), year)
    }

    /** « Je ne sais pas »: the answer shows all the same. */
    fun dontKnow() = send("", null)

    private fun send(who: String, year: Int?) {
        val r = repo ?: return
        val ready = uiState.value as? WhoIsItPostUiState.Ready ?: return
        if (!ready.canGuess || ready.revealed || draft.value.sending) return
        draft.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) { r.guess(postId, WhoIsItGuess(memberId, memberName, who, year)) } != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) memory?.let { it.save(memberId, GUESSED, it.load(memberId, GUESSED) + postId) }
            draft.update { if (ok) Draft() else it.copy(sending = false, failed = true) }
        }
    }

    fun delete() {
        val r = repo ?: return
        val ready = uiState.value as? WhoIsItPostUiState.Ready ?: return
        if (!ready.canDelete) return
        viewModelScope.launch {
            try {
                r.delete(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                draft.update { it.copy(failed = true) }
            }
        }
    }
}

// --- The Accueil: the newest photo still to guess ------------------------------------------------

/**
 * The photo the chip of the Accueil offers: the newest of someone else, neither set aside nor
 * guessed on this phone. Once shown it stays, answer included, until set aside or the next day.
 */
class WhoIsItHomeViewModel(
    repo: WhoIsItRepository?,
    private val memberId: String,
    private val memory: WhoIsItMemory?,
    val canDismiss: Boolean = true,
) : ViewModel() {

    private val guessedBefore = memory?.load(memberId, GUESSED).orEmpty()
    private val dismissed = MutableStateFlow(memory?.load(memberId, DISMISSED).orEmpty())
    private var shown: String? = null

    val postId: StateFlow<String?> = combine(repo?.observeLatest(RECENT) ?: flowOf(emptyList()), dismissed) { posts, gone ->
        val candidates = posts.filter { it.authorId != memberId && it.id !in gone }
        val kept = shown?.takeIf { id -> candidates.any { it.id == id } }
        (kept ?: candidates.firstOrNull { it.id !in guessedBefore }?.id).also { shown = it }
    }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** « Ne plus me montrer », for this photo and on this phone only. */
    fun dismiss(id: String) {
        if (!canDismiss) return
        dismissed.update { it + id }
        memory?.save(memberId, DISMISSED, dismissed.value)
    }

    private companion object {
        const val RECENT = 20
    }
}

// --- The list and the form ------------------------------------------------------------------------

sealed interface WhoIsItListUiState {
    data object Loading : WhoIsItListUiState
    data object Error : WhoIsItListUiState
    data class Ready(val posts: List<WhoIsItPost>) : WhoIsItListUiState
}

class WhoIsItListViewModel(repo: WhoIsItRepository?) : ViewModel() {
    val uiState: StateFlow<WhoIsItListUiState> = (repo?.observeLatest(LIST) ?: flowOf(emptyList()))
        .map<List<WhoIsItPost>, WhoIsItListUiState> { WhoIsItListUiState.Ready(it) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(WhoIsItListUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoIsItListUiState.Loading)

    private companion object {
        const val LIST = 100
    }
}

data class WhoIsItForm(val photo: String? = null, val hint: String = "", val who: String = "", val year: String = "")

enum class WhoIsItFormError { PhotoMissing, WhoMissing, YearInvalid }

enum class WhoIsItSaveFailure { NeedsNetwork, PhotoUnreadable, Failed }

sealed interface WhoIsItEditUiState {
    data class Editing(
        val form: WhoIsItForm = WhoIsItForm(),
        val errors: Set<WhoIsItFormError> = emptySet(),
        val sending: Boolean = false,
        val failure: WhoIsItSaveFailure? = null,
    ) : WhoIsItEditUiState

    data class Done(val postId: String) : WhoIsItEditUiState
}

/**
 * « Publier une photo »: the photo first, then the post and its answer, so a post never shows
 * without its photo. The text of the draft survives Android stopping the app; the photo is chosen
 * again.
 */
class WhoIsItEditViewModel(
    private val repo: WhoIsItRepository?,
    private val uploader: PhotoUploader?,
    private val memberId: String,
    private val memberName: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val isOnline: Flow<Boolean> = flowOf(true),
    private val storage: PhotoStorage? = null,
    private val thisYear: Int = Year.now().value,
    private val writeTimeoutMillis: Long = 10_000,
) : ViewModel() {

    private val state = MutableStateFlow<WhoIsItEditUiState>(
        WhoIsItEditUiState.Editing(WhoIsItForm(hint = savedState[HINT] ?: "", who = savedState[WHO] ?: "", year = savedState[YEAR] ?: "")),
    )
    val uiState: StateFlow<WhoIsItEditUiState> = state.asStateFlow()
    private var showErrors = false

    private fun validate(form: WhoIsItForm): Set<WhoIsItFormError> = buildSet {
        if (form.photo == null) add(WhoIsItFormError.PhotoMissing)
        if (form.who.isBlank()) add(WhoIsItFormError.WhoMissing)
        if (parseWhoIsItYear(form.year, thisYear).isFailure) add(WhoIsItFormError.YearInvalid)
    }

    private fun edit(change: (WhoIsItForm) -> WhoIsItForm) = state.update { current ->
        if (current !is WhoIsItEditUiState.Editing || current.sending) return@update current
        val form = change(current.form)
        savedState[HINT] = form.hint
        savedState[WHO] = form.who
        savedState[YEAR] = form.year
        current.copy(form = form, errors = if (showErrors) validate(form) else emptySet(), failure = null)
    }

    fun setPhoto(uri: String?) = edit { it.copy(photo = uri) }
    fun setHint(text: String) = edit { it.copy(hint = text.take(WhoIsItPost.MAX_HINT)) }
    fun setWho(text: String) = edit { it.copy(who = text.take(WhoIsItPost.MAX_WHO)) }
    fun setYear(text: String) = edit { it.copy(year = text.filter(Char::isDigit).take(4)) }

    fun publish() {
        val current = state.value as? WhoIsItEditUiState.Editing ?: return
        if (current.sending) return
        val errors = validate(current.form)
        if (errors.isNotEmpty()) {
            showErrors = true
            state.value = current.copy(errors = errors)
            return
        }
        viewModelScope.launch { state.value = write(current) }
    }

    private suspend fun write(s: WhoIsItEditUiState.Editing): WhoIsItEditUiState {
        val r = repo ?: return s.copy(failure = WhoIsItSaveFailure.Failed)
        val upload = uploader
        val photo = s.form.photo ?: return s
        if (upload == null || !isOnline.first()) return s.copy(failure = WhoIsItSaveFailure.NeedsNetwork)
        val post = WhoIsItPost(r.newPostId(), memberId, memberName, s.form.hint.trim(), null)
        val answer = WhoIsItAnswer(s.form.who.trim(), parseWhoIsItYear(s.form.year, thisYear).getOrNull())
        state.value = s.copy(sending = true, failure = null)
        return try {
            upload.upload(photo, post.photoPath, post.thumbPath)
            withTimeoutOrNull(writeTimeoutMillis) { r.publish(post, answer) } ?: throw IOException("timeout")
            listOf(HINT, WHO, YEAR).forEach { savedState.remove<Any>(it) }
            WhoIsItEditUiState.Done(post.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            removeFiles(post)
            val failure = when {
                e is IOException && e.message != "timeout" -> WhoIsItSaveFailure.PhotoUnreadable
                isOnline.first() -> WhoIsItSaveFailure.Failed
                else -> WhoIsItSaveFailure.NeedsNetwork
            }
            s.copy(sending = false, failure = failure)
        }
    }

    /** Best effort: a file left behind is only a photo nobody is shown. */
    private suspend fun removeFiles(post: WhoIsItPost) {
        val files = storage ?: return
        for (path in listOf(post.photoPath, post.thumbPath)) {
            try {
                files.delete(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never sent, or offline.
            }
        }
    }

    private companion object {
        const val HINT = "whoIsIt_hint"
        const val WHO = "whoIsIt_who"
        const val YEAR = "whoIsIt_year"
    }
}
