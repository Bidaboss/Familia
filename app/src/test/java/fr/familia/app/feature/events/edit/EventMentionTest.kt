package fr.familia.app.feature.events.edit

import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** « @Prénom » in the note of an event (l'admin, 19/11). Fictional family. */
class EventMentionTest {

    private val base = EventForm(title = "Pique-nique", date = LocalDate.of(2027, 6, 1), target = TestFamily.event("x", TestFamily.adult).target)

    @Test
    fun `the members named in the note are saved with the event, each once`() {
        val event = base.copy(note = "@Noé le pain", mentionIds = listOf("adult2", "adult2")).toEvent("e1", "adult1", "Adult1")
        assertEquals(listOf("adult2"), event.mentionIds)
    }

    @Test
    fun `no note, nobody named`() {
        val event = base.copy(note = "  ", mentionIds = listOf("adult2")).toEvent("e1", "adult1", "Adult1")
        assertEquals(emptyList<String>(), event.mentionIds)
    }

    @Test
    fun `an event opens with whom its note names`() {
        val old = TestFamily.event("e1", TestFamily.adult).copy(note = "@Noé", mentionIds = listOf("adult2"))
        assertEquals(listOf("adult2"), EventForm.from(old).mentionIds)
    }
}
