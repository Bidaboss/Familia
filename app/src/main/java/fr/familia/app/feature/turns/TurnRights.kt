package fr.familia.app.feature.turns

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Turn

/** What [me] may do on a rotation, as firebase/firestore.rules allows it (récit 58). A child reads. */
data class TurnRights(
    val edit: Boolean,
    val done: Boolean,
    val skip: Boolean,
    val askSwap: Boolean,
    val answerSwap: Boolean,
    val withdrawSwap: Boolean,
    val createEvent: Boolean,
)

fun turnRights(turn: Turn, me: Member, ownerId: String?): TurnRights {
    val adult = me.profile == MemberProfile.Adult
    val author = adult && (turn.createdBy == me.id || me.id == ownerId)
    val mine = adult && turn.currentHouseholdId == me.householdId
    val swap = turn.swap
    return TurnRights(
        edit = author,
        done = mine || author,
        skip = mine,
        askSwap = adult && swap == null && me.householdId in turn.householdIds,
        answerSwap = adult && swap != null && swap.toId == me.householdId,
        withdrawSwap = adult && swap != null && swap.fromId == me.householdId,
        createEvent = mine,
    )
}

/** Only adults create a rotation. */
fun canCreateTurn(me: Member): Boolean = me.profile == MemberProfile.Adult
