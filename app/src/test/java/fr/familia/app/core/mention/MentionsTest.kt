package fr.familia.app.core.mention

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « @Prénom » (Kévin, 19/11), with a fictional family. */
class MentionsTest {

    private fun member(id: String, name: String) =
        Member(id, name, null, null, "h1", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)

    private val noe = member("noe", "Noé")
    private val leo = member("leo", "Léo")
    private val leonie = member("leonie", "Léonie")
    private val ines = member("ines", "Inès")

    @Test
    fun `an at sign starting a word opens the suggestions, with what follows it`() {
        assertEquals(MentionQuery(6, "No"), activeMentionQuery("Merci @No", 9))
        assertEquals(MentionQuery(0, ""), activeMentionQuery("@", 1))
        assertEquals(MentionQuery(1, "in"), activeMentionQuery("(@in", 4))
    }

    @Test
    fun `no suggestions inside an e-mail, after a space or far from the at sign`() {
        assertNull(activeMentionQuery("noe@mail", 8))
        assertNull(activeMentionQuery("@Noé merci", 10))
        assertNull(activeMentionQuery("@" + "a".repeat(31), 32))
        assertNull(activeMentionQuery("Bonjour", 7))
    }

    @Test
    fun `first names match from their start, accents and case aside`() {
        assertEquals(listOf(leo, leonie), mentionMatches("leo", listOf(noe, leo, leonie, ines)))
        assertEquals(listOf(ines), mentionMatches("INE", listOf(noe, leo, leonie, ines)))
        assertEquals(listOf(noe, leo, leonie, ines), mentionMatches("", listOf(noe, leo, leonie, ines)))
    }

    @Test
    fun `a pick writes the first name and a space, the cursor after it`() {
        val typed = TextFieldValue("Bravo @no !", TextRange(9))
        val next = insertMention(typed, MentionQuery(6, "no"), noe)
        assertEquals("Bravo @Noé  !", next.text)
        assertEquals(TextRange(11), next.selection)
    }

    @Test
    fun `the longest name wins and a name must end its word`() {
        val text = "@Léonie et @Léo, pas @Léonard"
        val found = mentionRanges(text, listOf(leo, leonie))
        assertEquals(listOf("leonie" to 0..6, "leo" to 11..14), found.map { it.second.id to it.first })
        assertEquals(setOf("leonie", "leo"), mentionedIds(text, listOf(leo, leonie, ines)))
    }

    @Test
    fun `a backspace at the end of a name takes the whole name away`() {
        val old = TextFieldValue("Merci @Noé !", TextRange(10))
        val typed = TextFieldValue("Merci @No !", TextRange(9))
        val next = backspaceMention(old, typed, listOf(noe))
        assertEquals("Merci  !", next.text)
        assertEquals(TextRange(6), next.selection)
    }

    @Test
    fun `any other change goes through as typed`() {
        val old = TextFieldValue("Merci @Noé !", TextRange(12))
        val typed = TextFieldValue("Merci @Noé ", TextRange(11))
        assertEquals(typed, backspaceMention(old, typed, listOf(noe)))
        val added = TextFieldValue("Merci @Noé !!", TextRange(13))
        assertEquals(added, backspaceMention(old, added, listOf(noe)))
    }
}
