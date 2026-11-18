package fr.familia.app.feature.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth

/** What the foyer is preparing, step by step. */
data class HostDraft(
    val kind: HostedKind = HostedKind.Photo,
    val target: Int = HostGuide.suggestedTarget(0),
    val title: String = HostGuide.start(HostedKind.Photo, target).title,
    /** Once someone typed the title, choosing a kind or a target no longer rewrites it. */
    val titleEdited: Boolean = false,
    val emoji: String = HostGuide.start(HostedKind.Photo, target).emoji,
    val illustration: HostedIllustration = HostGuide.start(HostedKind.Photo, target).illustration,
    /** The optional word for the family, shown in « Comment ça marche » (récit 65). */
    val note: String = "",
)

data class HostGuideState(
    val month: YearMonth,
    /** 1 type, 2 objectif, 3 titre, emoji et illustration, 4 aperçu. */
    val step: Int = 1,
    val draft: HostDraft = HostDraft(),
    /** An adult of the foyer publishes or passes the turn; a child saves the draft for them. */
    val canPublish: Boolean = false,
    val saving: Boolean = false,
    /** Set once saved: the screen closes, with a word for what was done. */
    val done: HostedStatus? = null,
    val failed: Boolean = false,
)

/** The guide of the challenge of a foyer, in 4 steps (récit 35 bis, carte PRODUIT-51). */
class HostChallengeViewModel(
    private val tree: TreeRepository?,
    private val members: MemberRepository?,
    private val member: Member,
    month: YearMonth,
) : ViewModel() {
    private val _state = MutableStateFlow(HostGuideState(month, canPublish = member.profile == MemberProfile.Adult))
    val state: StateFlow<HostGuideState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val size = try {
                withTimeoutOrNull(3_000) { members?.observeFamily()?.first()?.members?.size } ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
            val target = HostGuide.suggestedTarget(size)
            val start = HostGuide.start(HostedKind.Photo, target)
            val saved = try {
                withTimeoutOrNull(3_000) { tree?.observeHosted(month)?.first() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val draft = if (saved != null && saved.status == HostedStatus.Draft && saved.householdId == member.householdId) {
                HostDraft(saved.kind, saved.target, saved.title, titleEdited = true, emoji = saved.emoji, illustration = saved.illustration, note = saved.note)
            } else {
                HostDraft(HostedKind.Photo, target, start.title, false, start.emoji, start.illustration)
            }
            _state.update { it.copy(draft = draft) }
        }
    }

    private fun edit(block: (HostDraft) -> HostDraft) = _state.update { it.copy(draft = block(it.draft)) }

    /** Step 1: the kind also proposes a title, an emoji and a drawing, until someone types a title. */
    fun chooseKind(kind: HostedKind) = edit { d ->
        val start = HostGuide.start(kind, d.target)
        if (d.titleEdited) d.copy(kind = kind) else d.copy(kind = kind, title = start.title, emoji = start.emoji, illustration = start.illustration)
    }

    /** Step 2. */
    fun setTarget(target: Int) = edit { d ->
        val t = target.coerceIn(HostedChallenge.TARGET_MIN, HostedChallenge.TARGET_MAX)
        if (d.titleEdited) d.copy(target = t) else d.copy(target = t, title = HostGuide.start(d.kind, t).title)
    }

    /** Step 3. */
    fun setTitle(title: String) = edit { it.copy(title = title.take(HostedChallenge.TITLE_MAX), titleEdited = true) }

    fun setEmoji(emoji: String) = edit { it.copy(emoji = emoji) }

    fun setNote(note: String) = edit { it.copy(note = note.take(HostedChallenge.NOTE_MAX)) }

    fun setIllustration(illustration: HostedIllustration) = edit { it.copy(illustration = illustration) }

    fun next() = _state.update { if (it.step == 3 && it.draft.title.isBlank()) it else it.copy(step = (it.step + 1).coerceAtMost(4)) }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }

    /** Saves the draft for the foyer. */
    fun saveDraft() = save(HostedStatus.Draft)

    /** An adult publishes: the challenge of the month, or of the next one when it starts. */
    fun publish() = save(HostedStatus.Published)

    /** « Passer notre tour »: the challenge of Familia stays, nobody in the family is told. */
    fun skip() = save(HostedStatus.Skipped)

    fun failureShown() = _state.update { it.copy(failed = false) }

    private fun save(status: HostedStatus) {
        val s = _state.value
        val repo = tree ?: return
        if (s.saving || (status != HostedStatus.Draft && !s.canPublish)) return
        val d = s.draft
        val title = d.title.trim().ifEmpty { HostGuide.start(d.kind, d.target).title }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.saveHosted(HostedChallenge(s.month, member.householdId, status, d.kind, title, d.emoji, d.illustration, d.target, d.note.trim()), member.id)
                _state.update { it.copy(saving = false, done = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}
