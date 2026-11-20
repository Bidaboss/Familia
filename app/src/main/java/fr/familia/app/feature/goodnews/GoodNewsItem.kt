package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Reaction

data class ReactionCount(val reaction: Reaction, val count: Int)

/** One good news as the screens show it, with the rights of the member looking at it. */
data class GoodNewsItem(
    val news: GoodNews,
    /** Null when the author left the family: the card then shows [GoodNews.authorName]. */
    val author: Member?,
    /** The child it is for, when it is not the author's own news. */
    val forName: String?,
    val mentionNames: List<String>,
    /** The six reactions, always in the same order. */
    val reactionCounts: List<ReactionCount>,
    val myReaction: Reaction?,
    val canEdit: Boolean,
    val canRemove: Boolean,
    /** The member it is for or a member named: « Masquer pour moi » (PRD rév. 158). */
    val canHide: Boolean = false,
    /** Hidden by the member looking at it: only the page of the post still shows it. */
    val isHiddenForMe: Boolean = false,
    /** First names of those who reacted, for « Qui a réagi » (PRODUIT-139). */
    val reactorNames: Map<String, String> = emptyMap(),
)

fun goodNewsItem(news: GoodNews, me: Member, family: Family): GoodNewsItem {
    fun name(id: String) = family.members.firstOrNull { it.id == id }?.firstName
    return GoodNewsItem(
        news = news,
        author = family.members.firstOrNull { it.id == news.authorId },
        forName = news.forMemberId.takeIf { it != news.authorId }?.let(::name),
        mentionNames = news.mentionIds.mapNotNull(::name),
        reactionCounts = Reaction.entries.map { r -> ReactionCount(r, news.reactions.values.count { it == r }) },
        myReaction = news.reactions[me.id],
        canEdit = canEditGoodNews(news, me),
        canRemove = canRemoveGoodNews(news, me, family),
        canHide = news.isAbout(me.id),
        isHiddenForMe = news.isHiddenFor(me.id),
        reactorNames = news.reactions.keys.mapNotNull { id -> name(id)?.let { id to it } }.toMap(),
    )
}

/** Touching the reaction one already gave takes it back. */
fun nextReaction(current: Reaction?, touched: Reaction): Reaction? = if (current == touched) null else touched
