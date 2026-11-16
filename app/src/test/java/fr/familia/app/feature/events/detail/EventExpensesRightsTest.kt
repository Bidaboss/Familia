package fr.familia.app.feature.events.detail

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.edit.TargetChoice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventExpensesRightsTest {

    private val camille = Member("m1", "Camille", null, null, "h1", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)
    private val ines = Member("m2", "Inès", null, null, "h1", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)
    private val noe = Member("m3", "Noé", null, null, "h1", MemberProfile.ChildWithApp, isAdmin = false, parentIds = listOf("m1"), uid = null)

    private fun state(author: Member, me: Member, myAnswer: RsvpStatus?) = EventDetailUiState.Success(
        event = Event("e1", "Repas chez Inès", LocalDate.of(2026, 12, 5), null, null, null, EventTarget.ForFamily, author.id, author.firstName),
        today = LocalDate.of(2026, 11, 21),
        target = TargetChoice(EventTarget.ForFamily, null),
        authorName = author.firstName,
        canManage = author.id == me.id,
        answerers = listOf(Answerer(me, myAnswer, isMe = true)),
        coming = emptyList(),
        maybe = emptyList(),
        notComing = emptyList(),
    )

    @Test
    fun `an adult who comes, or the author, opens the accounts of the event`() {
        assertTrue(canOpenExpenses(state(author = ines, me = camille, myAnswer = RsvpStatus.Yes), camille))
        assertTrue(canOpenExpenses(state(author = camille, me = camille, myAnswer = null), camille))
    }

    @Test
    fun `maybe, no answer or a child do not`() {
        assertFalse(canOpenExpenses(state(author = ines, me = camille, myAnswer = RsvpStatus.Maybe), camille))
        assertFalse(canOpenExpenses(state(author = ines, me = camille, myAnswer = null), camille))
        assertFalse(canOpenExpenses(state(author = ines, me = noe, myAnswer = RsvpStatus.Yes), noe))
    }
}
