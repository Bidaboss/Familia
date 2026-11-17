package fr.familia.app.feature.goodnews

import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.photo.PhotoUploader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/** A fictional good news by [author], published [daysAgo] days before 21/11/2026 at 16 h UTC. */
fun goodNews(id: String, author: Member, forMemberId: String = author.id, daysAgo: Long = 0, photos: Int = 1) = GoodNews(
    id = id, text = "Nouvelle $id", photoIds = (1..photos).map { "p$it" },
    authorId = author.id, authorName = author.firstName, forMemberId = forMemberId, householdId = author.householdId,
    createdAt = NOW.minusSeconds(daysAgo * 86_400), updatedAt = NOW.minusSeconds(daysAgo * 86_400),
)

val NOW: Instant = Instant.parse("2026-11-21T16:00:00Z")

class FakeGoodNews(initial: List<GoodNews> = emptyList(), private val failWith: Exception? = null) : GoodNewsRepository {
    val posts = MutableStateFlow(initial)
    val published = mutableListOf<GoodNews>()
    private var ids = 0

    override fun observeLatest(count: Int): Flow<List<GoodNews>> =
        posts.map { list -> list.sortedByDescending { it.createdAt }.take(count) }

    override fun observePost(id: String): Flow<GoodNews?> = posts.map { list -> list.firstOrNull { it.id == id } }

    override fun newId(): String = "id${++ids}"

    override suspend fun publish(news: GoodNews) {
        failWith?.let { throw it }
        published += news
        posts.value = posts.value + news.copy(createdAt = NOW, updatedAt = NOW)
    }

    override suspend fun edit(id: String, text: String, mentionIds: List<String>) = change(id) { it.copy(text = text, mentionIds = mentionIds) }

    override suspend fun react(id: String, memberId: String, reaction: Reaction?) = change(id) {
        it.copy(reactions = if (reaction == null) it.reactions - memberId else it.reactions + (memberId to reaction))
    }

    override suspend fun setHidden(id: String, memberId: String, hidden: Boolean) = change(id) {
        it.copy(hiddenByIds = if (hidden) (it.hiddenByIds + memberId).distinct() else it.hiddenByIds - memberId)
    }

    override suspend fun remove(id: String) {
        failWith?.let { throw it }
        posts.value = posts.value.filter { it.id != id }
    }

    private fun change(id: String, block: (GoodNews) -> GoodNews) {
        failWith?.let { throw it }
        posts.value = posts.value.map { if (it.id == id) block(it) else it }
    }
}

/**
 * Remembers what was sent; [unreadable] makes the encoder fail like a broken file. [failures] are
 * thrown by the next sends, one each, like a network that drops; [gate], when set, holds each send
 * after it has told [halfway] of its progress.
 */
class FakeUploader(private val unreadable: Boolean = false) : PhotoUploader {
    val sent = mutableListOf<Triple<String, String, String>>()
    val tries = mutableListOf<String>()
    val failures = ArrayDeque<Exception>()
    var gate: CompletableDeferred<Unit>? = null
    var halfway: Float = 0.5f

    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) =
        upload(source, photoPath, thumbnailPath) { }

    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String, onProgress: (Float) -> Unit) {
        if (unreadable) throw IOException("Not a picture")
        tries += source
        failures.removeFirstOrNull()?.let { throw it }
        gate?.let { onProgress(halfway); it.await() }
        onProgress(1f)
        sent += Triple(source, photoPath, thumbnailPath)
    }
}
