package fr.familia.app.core.data

import fr.familia.app.core.model.Moment
import fr.familia.app.core.model.MomentCheer
import kotlinx.coroutines.flow.Flow

/** « Pensez à moi » (récit 43). Screens never talk to Firestore directly. */
interface MomentRepository {
    /**
     * The moments of [memberId], soonest first, updated live. [withPending] for the member, the
     * author or a responsible adult only: the rules let nobody else read a moment waiting for a teenager.
     */
    fun observeFor(memberId: String, withPending: Boolean): Flow<List<Moment>>

    /** The encouragements of a moment, oldest first, updated live. */
    fun observeCheers(momentId: String): Flow<List<MomentCheer>>

    /** Notes a new moment (its id is chosen here). */
    suspend fun add(moment: Moment)

    /** The teenager accepts what a parent noted. */
    suspend fun accept(momentId: String)

    /** Takes a moment back, or refuses it. */
    suspend fun remove(momentId: String)

    suspend fun cheer(cheer: MomentCheer)

    suspend fun removeCheer(cheer: MomentCheer)
}
