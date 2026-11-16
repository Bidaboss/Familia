package fr.familia.app.core.data

import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import kotlinx.coroutines.flow.Flow

/** L'arbre généalogique (PRD, récit 40). See docs/data-model.md. */
interface GenealogyRepository {
    /** The relatives without an account, updated live. */
    fun observeKin(): Flow<List<Kin>>

    fun observeLinks(): Flow<List<KinLink>>

    /** The words [memberId] chose for the people of the tree, by person id. Theirs alone. */
    fun observeWords(memberId: String): Flow<Map<String, String>>

    fun newKinId(): String

    /** Adds or edits a relative, signed by [byMemberId]; with [firstLink], both go in one batch. */
    suspend fun saveKin(kin: Kin, byMemberId: String, firstLink: KinLink? = null)

    /** The server then removes their links. */
    suspend fun deleteKin(id: String)

    suspend fun addLink(link: KinLink, byMemberId: String)

    suspend fun removeLink(link: KinLink)

    suspend fun saveWords(memberId: String, words: Map<String, String>)
}
