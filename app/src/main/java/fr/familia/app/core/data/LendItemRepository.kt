package fr.familia.app.core.data

import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The things of « On se prête » (PRD, récit 51). See docs/data-model.md, lendItems and lendLoans. */
interface LendItemRepository {
    /**
     * Every thing, with its loan when [readerId] is its owner or borrower; for anyone else a thing
     * asked or lent is only « Prêté » ([fr.familia.app.core.model.withoutLoan]).
     */
    fun observeAll(readerId: String): Flow<List<LendItem>>

    fun observe(id: String, readerId: String): Flow<LendItem?>

    /** The thing itself, for its form: never the loan. */
    suspend fun get(id: String): LendItem?

    fun newId(): String

    /** Writes the thing itself; [isNew] makes it free and sets its creation time. Never the loan. */
    suspend fun save(item: LendItem, isNew: Boolean)

    suspend fun delete(id: String)

    /** « Je voudrais l'emprunter » a thing of [ownerId], in the name of [me], with optional dates. */
    suspend fun ask(id: String, ownerId: String, me: Member, from: Instant?, until: Instant?)

    /** « D'accord », by the owner. */
    suspend fun accept(id: String)

    /** « Pas cette fois », a dropped ask or « Rendu »: the thing is free again. */
    suspend fun release(id: String)
}
