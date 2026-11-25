package fr.familia.app.feature.polls.edit

import fr.familia.app.core.model.Poll
import java.time.LocalDate

/**
 * What a poll asked from an event is about (retour de l'admin, 18/11): it only fills the question
 * and the type, and is never stored. A poll asked from Sondages or the Accueil has none.
 */
enum class PollMotive(val kind: Poll.Kind) {
    Date(Poll.Kind.Date),
    Place(Poll.Kind.Choice),
    Menu(Poll.Kind.Choice),
    Other(Poll.Kind.Choice),
}

/** The event a poll is asked from, shown at the top of the form, never changed there. */
data class PollEvent(val title: String, val date: LocalDate)

/** [motive] chosen, its type set and its question written in, still to change as one likes. */
fun PollForm.withMotive(motive: PollMotive, question: String): PollForm =
    copy(motive = motive, kind = motive.kind, question = question)
