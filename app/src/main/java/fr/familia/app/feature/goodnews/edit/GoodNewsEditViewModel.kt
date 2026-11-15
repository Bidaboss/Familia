package fr.familia.app.feature.goodnews.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.feature.goodnews.goodNewsTargets
import fr.familia.app.feature.goodnews.mentionCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * « Publier une Bonne nouvelle », or change one's own ([postId]). The photos go to Storage first,
 * then the document is written, so nobody ever sees a good news whose photos are missing.
 */
class GoodNewsEditViewModel(
    private val goodNews: GoodNewsRepository?,
    private val members: MemberRepository?,
    private val uploader: PhotoUploader?,
    private val currentMemberId: String,
    private val postId: String?,
    private val isOnline: Flow<Boolean> = flowOf(true),
) : ViewModel() {

    private val state = MutableStateFlow<GoodNewsEditUiState>(GoodNewsEditUiState.Loading)
    val uiState: StateFlow<GoodNewsEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = runCatching { initial() }.getOrDefault(GoodNewsEditUiState.Unavailable) }
    }

    private suspend fun initial(): GoodNewsEditUiState {
        val family = members?.observeFamily()?.first() ?: return GoodNewsEditUiState.Unavailable
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return GoodNewsEditUiState.Unavailable
        val targets = goodNewsTargets(me, family)
        val candidates = mentionCandidates(me, family)
        if (postId == null) return GoodNewsEditUiState.Editing(GoodNewsForm(forMemberId = me.id), null, targets, candidates)
        val news = goodNews?.observePost(postId)?.first()
        if (news == null || news.authorId != me.id) return GoodNewsEditUiState.Unavailable
        val form = GoodNewsForm(text = news.text, forMemberId = news.forMemberId, mentionIds = news.mentionIds.toSet())
        return GoodNewsEditUiState.Editing(form, news, targets, candidates)
    }

    private fun edit(change: (GoodNewsEditUiState.Editing) -> GoodNewsEditUiState.Editing) {
        state.update { current -> if (current is GoodNewsEditUiState.Editing && !current.isSaving) change(current) else current }
    }

    fun setText(text: String) = edit { it.copy(form = it.form.copy(text = text), errors = it.errors - GoodNewsFormError.TextMissing) }

    fun addPhotos(uris: List<String>) = edit {
        if (!it.isNew) it else it.copy(form = it.form.withPhotos(uris), errors = it.errors - GoodNewsFormError.NoPhoto)
    }

    fun removePhoto(uri: String) = edit { it.copy(form = it.form.copy(photos = it.form.photos - uri)) }

    fun setFor(memberId: String) = edit { s ->
        if (!s.isNew || s.targets.none { it.id == memberId }) s else s.copy(form = s.form.copy(forMemberId = memberId))
    }

    fun toggleMention(memberId: String) = edit {
        val ids = it.form.mentionIds
        it.copy(form = it.form.copy(mentionIds = if (memberId in ids) ids - memberId else ids + memberId))
    }

    fun dismissFailure() = edit { it.copy(failure = null) }

    fun save() {
        val current = state.value as? GoodNewsEditUiState.Editing ?: return
        if (current.isSaving) return
        val errors = current.form.validate(current.isNew)
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        viewModelScope.launch {
            val existing = current.existing
            val result = if (existing == null) publish(current) else change(existing, current.form)
            state.value = result
        }
    }

    private suspend fun publish(s: GoodNewsEditUiState.Editing): GoodNewsEditUiState {
        val repo = goodNews ?: return s.copy(failure = PublishFailure.Failed)
        val upload = uploader ?: return s.copy(failure = PublishFailure.Failed)
        // Photos need the network: say so at once rather than wait.
        if (!isOnline.first()) return s.copy(failure = PublishFailure.NeedsNetwork)
        val me = s.targets.first()
        val forMember = s.targets.firstOrNull { it.id == s.form.forMemberId } ?: me
        val draft = GoodNews(
            id = repo.newId(),
            text = s.form.text.trim(),
            photoIds = s.form.photos.map { repo.newId() },
            authorId = me.id,
            authorName = me.firstName,
            forMemberId = forMember.id,
            householdId = forMember.householdId,
            mentionIds = s.form.mentionIds.filter { id -> s.candidates.any { it.id == id } }.sorted(),
        )
        return try {
            s.form.photos.zip(draft.photoIds).forEachIndexed { index, (uri, photoId) ->
                state.value = s.copy(sendingPhoto = index + 1, failure = null)
                upload.upload(uri, draft.photoPath(photoId), draft.thumbnailPath(photoId))
            }
            state.value = s.copy(sendingPhoto = 0, failure = null)
            // The write reaches the local cache at once; past 10 s it is queued and goes later.
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.publish(draft) }
            GoodNewsEditUiState.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            s.copy(sendingPhoto = null, failure = PublishFailure.PhotoUnreadable)
        } catch (e: Exception) {
            s.copy(sendingPhoto = null, failure = if (isOnline.first()) PublishFailure.Failed else PublishFailure.NeedsNetwork)
        }
    }

    private suspend fun change(existing: GoodNews, form: GoodNewsForm): GoodNewsEditUiState {
        val current = state.value as GoodNewsEditUiState.Editing
        val repo = goodNews ?: return current.copy(failure = PublishFailure.Failed)
        return try {
            state.value = current.copy(sendingPhoto = 0)
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { repo.edit(existing.id, form.text.trim(), form.mentionIds.sorted()) }
            GoodNewsEditUiState.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            current.copy(sendingPhoto = null, failure = PublishFailure.Failed)
        }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 10_000L
    }
}
