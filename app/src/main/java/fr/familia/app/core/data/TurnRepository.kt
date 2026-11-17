package fr.familia.app.core.data

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Turn
import kotlinx.coroutines.flow.Flow

/** The rotations of « À qui le tour ? » (PRD, récit 58). See docs/data-model.md, turns. */
interface TurnRepository {
    fun observeAll(): Flow<List<Turn>>

    fun observe(id: String): Flow<Turn?>

    fun newId(): String

    /** A new rotation by [me], from its first household, with no history. */
    suspend fun create(id: String, title: String, householdIds: List<String>, me: Member)

    /** The author or the admin principal: the title, the households, their order, whose turn it is. */
    suspend fun edit(id: String, title: String, householdIds: List<String>, current: Int)

    /** « C'est fait », with an optional label (« Noël 2026 »). */
    suspend fun done(turn: Turn, label: String)

    /** « On passe notre tour ». */
    suspend fun skip(turn: Turn)

    /** An exchange proposed by [me] with [toHouseholdId]. */
    suspend fun askSwap(turn: Turn, me: Member, toHouseholdId: String)

    /** The other household says yes. */
    suspend fun acceptSwap(turn: Turn)

    /** The other household says no, or the proposer withdraws it. */
    suspend fun dropSwap(id: String)

    suspend fun delete(id: String)
}
