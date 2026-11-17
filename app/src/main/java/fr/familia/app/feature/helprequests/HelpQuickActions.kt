package fr.familia.app.feature.helprequests

import fr.familia.app.core.data.AlreadyTakenException
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.feature.helprequests.detail.HelpActionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/*
 * « Je m'en occupe » and « Pas dispo » straight from a card (présentation R1 bis, §2 point 4 and
 * §4): on the Accueil, in À faire, and at the top of Entraide. The sheet of the request keeps its
 * own buttons; these do the same writes, which the rules check (firebase/firestore.rules).
 */

/** A request that waits for [me]: open, from today on, I may take it and did not say no. */
fun needsMe(request: HelpRequest, me: Member, today: LocalDate): Boolean =
    request.date >= today && canVolunteer(request, me) && request.declinedBy != me.id && me.id !in request.unavailableIds

/** I said « Pas dispo » (or « Je ne peux pas ») to this open request: it can still be taken. */
fun iSaidNo(request: HelpRequest, me: Member): Boolean =
    canVolunteer(request, me) && (request.declinedBy == me.id || me.id in request.unavailableIds)

/** « Je m'en occupe »: online only, so that there are never two volunteers. Null when done. */
suspend fun takeHelp(repo: HelpRequestRepository, request: HelpRequest, me: Member, timeoutMillis: Long = 8_000): HelpActionError? = try {
    val done = withTimeoutOrNull(timeoutMillis) { repo.take(request.id, me.id, me.firstName) }
    if (done == null) HelpActionError.NeedsNetwork else null
} catch (e: CancellationException) {
    throw e
} catch (e: AlreadyTakenException) {
    HelpActionError.AlreadyTaken(e.volunteerName)
} catch (e: Exception) {
    HelpActionError.NeedsNetwork
}

/**
 * « Pas dispo »: the only person warned says « Je ne peux pas », which tells the author (PRODUIT-22);
 * when others were warned too, only I stop seeing it as for me. Offline, it waits in the cache.
 */
suspend fun declineHelp(repo: HelpRequestRepository, request: HelpRequest, me: Member, timeoutMillis: Long = 4_000): HelpActionError? = try {
    withTimeoutOrNull(timeoutMillis) {
        if (canDecline(request, me)) repo.decline(request.id, me.id) else repo.markUnavailable(request.id, me.id)
    }
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    HelpActionError.Failed
}
