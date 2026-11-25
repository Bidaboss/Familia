package fr.familia.app.core.mention

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import fr.familia.app.core.model.Member
import java.text.Normalizer

/**
 * « @Prénom » in a text (l'admin, 19/11): typing « @ » suggests the members, a chosen one is written
 * « @Prénom », kept in mentionIds, and a backspace takes the whole name away. The text itself stays
 * plain: the names are found again from the members named.
 */

/** The « @… » being typed just before [cursor]: where its « @ » is, and what follows it. */
data class MentionQuery(val start: Int, val query: String)

/** Longest written name of a member to look for after « @ ». */
private const val MAX_QUERY = 30

fun activeMentionQuery(text: String, cursor: Int): MentionQuery? {
    if (cursor < 1 || cursor > text.length) return null
    val at = text.lastIndexOf('@', cursor - 1)
    if (at < 0 || cursor - at - 1 > MAX_QUERY) return null
    // « @ » starts a word: not inside an e-mail address.
    if (at > 0 && !text[at - 1].isWhitespace() && text[at - 1] !in "([«\"'") return null
    val query = text.substring(at + 1, cursor)
    if (query.any { it.isWhitespace() || it == '@' }) return null
    return MentionQuery(at, query)
}

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** The members whose first name starts with [query], accents and case aside. */
fun mentionMatches(query: String, members: List<Member>): List<Member> {
    val q = fold(query)
    return members.filter { fold(it.firstName).startsWith(q) }
}

/** « @que| » becomes « @Prénom | ». */
fun insertMention(value: TextFieldValue, mention: MentionQuery, member: Member): TextFieldValue {
    val end = value.selection.start
    val inserted = "@${member.firstName} "
    val text = value.text.substring(0, mention.start) + inserted + value.text.substring(end)
    return TextFieldValue(text, TextRange(mention.start + inserted.length))
}

/** Where each « @Prénom » of [members] is in [text], in order, never overlapping. */
fun mentionRanges(text: String, members: List<Member>): List<Pair<IntRange, Member>> {
    val found = mutableListOf<Pair<IntRange, Member>>()
    // Longer names first, so « @Léonie » is not read as « @Léo ».
    members.distinctBy { it.id }.sortedByDescending { it.firstName.length }.forEach { member ->
        val token = "@${member.firstName}"
        var from = 0
        while (true) {
            val at = text.indexOf(token, from)
            if (at < 0) break
            val end = at + token.length
            val endsWord = end == text.length || !text[end].isLetterOrDigit()
            val range = at until end
            if (endsWord && found.none { (r, _) -> r.first <= range.last && range.first <= r.last }) found += range to member
            from = end
        }
    }
    return found.sortedBy { it.first.first }
}

/** The members still named in [text], among those chosen. */
fun mentionedIds(text: String, chosen: List<Member>): Set<String> =
    mentionRanges(text, chosen).map { it.second.id }.toSet()

/**
 * A backspace at the end of (or inside) a « @Prénom » takes the whole name away. Any other change
 * goes through as typed.
 */
fun backspaceMention(old: TextFieldValue, new: TextFieldValue, chosen: List<Member>): TextFieldValue {
    val cursor = old.selection.start
    val oneCharBack = old.selection.collapsed && cursor > 0 &&
        new.text.length == old.text.length - 1 &&
        new.text == old.text.removeRange(cursor - 1, cursor)
    if (!oneCharBack) return new
    val hit = mentionRanges(old.text, chosen).firstOrNull { (range, _) -> cursor - 1 in range } ?: return new
    val range = hit.first
    return TextFieldValue(old.text.removeRange(range.first, range.last + 1), TextRange(range.first))
}
