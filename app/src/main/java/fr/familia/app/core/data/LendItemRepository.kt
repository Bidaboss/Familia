package fr.familia.app.core.data

import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The things of « On se prête » (PRD, récit 51). See docs/data-model.md, lendItems. */
interface LendItemRepository {
    fun observeAll(): Flow<List<LendItem>>

    fun observe(id: String): Flow<LendItem?>

    suspend fun get(id: String): LendItem?

    fun newId(): String

    /** Writes the thing itself; [isNew] makes it free and sets its creation time. Never the loan. */
    suspend fun save(item: LendItem, isNew: Boolean)

    suspend fun delete(id: String)

    /** « Je voudrais l'emprunter », in the name of [me], with optional dates. */
    suspend fun ask(id: String, me: Member, from: Instant?, until: Instant?)

    /** « D'accord », by the owner. */
    suspend fun accept(id: String)

    /** « Pas cette fois », a dropped ask or « Rendu »: the thing is free again. */
    suspend fun release(id: String)
}
