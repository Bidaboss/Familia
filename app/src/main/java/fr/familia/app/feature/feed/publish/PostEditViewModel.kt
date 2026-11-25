package fr.familia.app.feature.feed.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GazettePostRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.model.gazetteMentionCandidates
import fr.familia.app.core.photo.PhotoRefusedException
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.ui.pickGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * « Publier dans la gazette » (récit 117, fiche M1), or « Modifier le texte » of one's own
 * publication ([postId], fiche R4). The photos go to Storage first, then the document is written,
 * so nobody ever sees a publication whose photos are missing.
 */
class PostEditViewModel(
    private val posts: GazettePostRepository?,
    private val members: MemberRepository?,
    private val uploader: PhotoUploader?,
    private val currentMemberId: String,
    private val postId: String?,
    private val isOnline: Flow<Boolean> = flowOf(true),
) : ViewModel() {

    private val state = MutableStateFlow<PostEditUiState>(PostEditUiState.Loading)
    val uiState: StateFlow<PostEditUiState> = state.asStateFlow()

    private var sending: Job? = null

    /** Kept from one try to the next: the same id, so a retry never makes a second publication. */
    private var pendingId: String? = null
    private val sentPhotos = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            state.value = runCatching { initial() }.getOrDefault(PostEditUiState.Unavailable)
            isOnline.catch { }.collect { online ->
                state.update { if (it is PostEditUiState.Editing) it.copy(offline = !online) else it }
            }
        }
    }

    private suspend fun initial(): PostEditUiState {
        val family = members?.observeFamily()?.first() ?: return PostEditUiState.Unavailable
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return PostEditUiState.Unavailable
        val candidates = gazetteMentionCandidates(me, family)
        val groups = pickGroups(candidates, family, me.householdId)
        if (postId == null) return PostEditUiState.Editing(PostForm(), null, me, candidates, groups)
        val post = posts?.observePost(postId)?.first()
        if (post == null || post.authorId != me.id) return PostEditUiState.Unavailable
        val form = PostForm(text = post.text, mentionIds = post.mentionIds.toSet())
        return PostEditUiState.Editing(form, post, me, candidates, groups)
    }

    private fun edit(change: (PostEditUiState.Editing) -> PostEditUiState.Editing) {
        state.update { current -> if (current is PostEditUiState.Editing && !current.isSaving) change(current) else current }
    }

    fun setText(text: String) = edit { it.copy(form = it.form.copy(text = text), errors = emptySet()) }

    fun addPhotos(uris: List<String>) = edit { if (!it.isNew) it else it.copy(form = it.form.withPhotos(uris), errors = emptySet()) }

    fun removePhoto(uri: String) = edit { it.copy(form = it.form.copy(photos = it.form.photos - uri)) }

    fun setMentions(ids: Set<String>) = edit { it.copy(form = it.form.copy(mentionIds = ids)) }

    fun dismissFailure() = edit { it.copy(failure = null) }

    fun save() {
        val current = state.value as? PostEditUiState.Editing ?: return
        if (current.isSaving) return
        val errors = current.form.validate(current.form.photos.isNotEmpty() || current.existing?.photoIds?.isNotEmpty() == true)
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        sending = viewModelScope.launch {
            val existing = current.existing
            state.value = if (existing == null) publish(current) else change(existing, current)
        }
    }

    /** « Annuler l'envoi »: the form stays; what was already sent is kept for the next try. */
    fun cancelSending() {
        val current = state.value as? PostEditUiState.Editing ?: return
        if (!current.isNew || (current.sendingPhoto ?: 0) == 0) return
        sending?.cancel()
        sending = null
        state.value = current.copy(sendingPhoto = null, progress = null, failure = null)
    }

    private fun sendingState(s: PostEditUiState.Editing, photo: Int, progress: Float?): PostEditUiState.Editing =
        (state.value as? PostEditUiState.Editing ?: s).copy(sendingPhoto = photo, progress = progress, failure = null)

    /** Sends one photo; without network it waits, with network it tries again [ONLINE_RETRIES] times. */
    private suspend fun sendPhoto(s: PostEditUiState.Editing, index: Int, total: Int, send: suspend (onProgress: (Float) -> Unit) -> Unit) {
        var failures = 0
        while (true) {
            if (!isOnline.first()) {
                state.value = sendingState(s, index + 1, index.toFloat() / total).copy(offline = true)
                isOnline.first { it }
            }
            state.value = sendingState(s, index + 1, index.toFloat() / total)
            try {
                send { done ->
                    val part = done.coerceIn(0f, 1f)
                    state.update { cur ->
                        if (cur is PostEditUiState.Editing && cur.sendingPhoto == index + 1) cur.copy(progress = (index + part) / total) else cur
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: PhotoRefusedException) {
                throw e
            } catch (e: Exception) {
                if (!isOnline.first()) continue
                if (++failures > ONLINE_RETRIES) throw e
                delay(RETRY_DELAY_MS * failures)
            }
        }
    }

    private suspend fun publish(s: PostEditUiState.Editing): PostEditUiState {
        val repo = posts ?: return s.copy(failure = PostFailure.Failed)
        val upload = uploader
        if (s.form.photos.isNotEmpty() && upload == null) return s.copy(failure = PostFailure.Failed)
        val id = pendingId ?: repo.newId().also { pendingId = it }
        val draft = GazettePost(
            id = id,
            authorId = s.me.id,
            authorName = s.me.firstName,
            text = s.form.text.trim(),
            // A photo not fully sent gets a new id: Storage never replaces a file.
            photoIds = s.form.photos.map { uri -> sentPhotos[uri] ?: repo.newId() },
            mentionIds = mentionsOf(s),
        )
        val total = s.form.photos.size
        return try {
            s.form.photos.zip(draft.photoIds).forEachIndexed { index, (uri, photoId) ->
                if (sentPhotos[uri] == photoId) return@forEachIndexed
                sendPhoto(s, index, total) { onProgress -> upload!!.upload(uri, draft.photoPath(photoId), draft.thumbnailPath(photoId), onProgress) }
                sentPhotos[uri] = photoId
            }
            state.value = sendingState(s, 0, 1f)
            // The write reaches the local cache at once; past 10 s it is queued and goes later.
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.publish(draft) }
            PostEditUiState.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            (state.value as? PostEditUiState.Editing ?: s).copy(sendingPhoto = null, progress = null, failure = PostFailure.PhotoUnreadable)
        } catch (e: Exception) {
            (state.value as? PostEditUiState.Editing ?: s).copy(sendingPhoto = null, progress = null, failure = PostFailure.Failed)
        }
    }

    private suspend fun change(existing: GazettePost, s: PostEditUiState.Editing): PostEditUiState {
        val repo = posts ?: return s.copy(failure = PostFailure.Failed)
        return try {
            state.value = s.copy(sendingPhoto = 0)
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.edit(existing.id, s.form.text.trim(), mentionsOf(s)) }
            PostEditUiState.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            s.copy(sendingPhoto = null, failure = PostFailure.Failed)
        }
    }

    /** Only those who may be named, 20 at most (the rules count them). */
    private fun mentionsOf(s: PostEditUiState.Editing): List<String> =
        s.form.mentionIds.filter { id -> s.candidates.any { it.id == id } }.sorted().take(GazettePost.MENTIONS_MAX)

    private companion object {
        const val WRITE_TIMEOUT_MS = 10_000L
        const val ONLINE_RETRIES = 2
        const val RETRY_DELAY_MS = 3_000L
    }
}
