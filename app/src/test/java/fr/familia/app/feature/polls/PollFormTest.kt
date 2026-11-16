package fr.familia.app.feature.polls

import fr.familia.app.core.model.Poll
import fr.familia.app.feature.polls.edit.DateChoice
import fr.familia.app.feature.polls.edit.PollForm
import fr.familia.app.feature.polls.edit.PollFormError
import fr.familia.app.feature.polls.edit.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class PollFormTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `dates are stored in order, without duplicates or empty slots`() {
        val form = PollForm(
            dates = listOf(
                DateChoice(LocalDate.of(2026, 12, 20), LocalTime.of(12, 30)),
                DateChoice(LocalDate.of(2026, 12, 19)),
                DateChoice(),
                DateChoice(LocalDate.of(2026, 12, 19)),
            ),
        )
        assertEquals(listOf("2026-12-19", "2026-12-20 12:30"), form.options())
    }

    @Test
    fun `free answers are trimmed and kept once`() {
        val form = PollForm(kind = Poll.Kind.Choice, choices = listOf(" Pizza ", "pizza", "", "Crêpes"))
        assertEquals(listOf("Pizza", "Crêpes"), form.options())
    }

    @Test
    fun `the form asks for a question, two answers and someone`() {
        val errors = validate(PollForm(), today)
        assertEquals(setOf(PollFormError.QuestionMissing, PollFormError.TooFewOptions, PollFormError.NobodyAsked), errors)
        val ok = PollForm(question = "Quand ?", dates = listOf(DateChoice(today), DateChoice(today.plusDays(1))), askedIds = setOf("a"))
        assertEquals(emptySet<PollFormError>(), validate(ok, today))
    }

    @Test
    fun `a past date or end date is refused`() {
        val form = PollForm(
            question = "Quand ?",
            dates = listOf(DateChoice(today.minusDays(1)), DateChoice(today.plusDays(1)), DateChoice(today.plusDays(2))),
            askedIds = setOf("a"),
            closesOn = today.minusDays(1),
        )
        assertEquals(setOf(PollFormError.DatePast, PollFormError.ClosesPast), validate(form, today))
    }

    @Test
    fun `a too long answer or question is refused`() {
        val form = PollForm(kind = Poll.Kind.Choice, question = "x".repeat(121), choices = listOf("a", "b".repeat(81)), askedIds = setOf("a"))
        assertTrue(PollFormError.QuestionTooLong in validate(form, today))
        assertTrue(PollFormError.OptionTooLong in validate(form, today))
    }

    @Test
    fun `the end date takes answers until its midnight in Paris`() {
        // Winter: Paris is UTC+1, so 1 December ends at 23:00 UTC.
        assertEquals(Instant.parse("2026-12-01T23:00:00Z"), closesAtFor(LocalDate.of(2026, 12, 1)))
        assertEquals(LocalDate.of(2026, 12, 1), lastDayOf(Instant.parse("2026-12-01T23:00:00Z")))
    }
}
