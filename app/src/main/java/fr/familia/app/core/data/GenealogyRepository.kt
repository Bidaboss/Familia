package fr.familia.app.core.data

import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import kotlinx.coroutines.flow.Flow

/** A new photo for a relative of the tree, or none any more (récit 79). */
sealed interface KinPhotoChange {
    /** A square JPEG made by the app, like a profile photo. */
    class Set(val jpeg: ByteArray) : KinPhotoChange

    data object Remove : KinPhotoChange
}

/** L'arbre généalogique (PRD, récit 40). See docs/data-model.md. */
interface GenealogyRepository {
    /** The relatives without an account, updated live. */
    fun observeKin(): Flow<List<Kin>>

    fun observeLinks(): Flow<List<KinLink>>

    /** The words [memberId] chose for the people of the tree, by person id. Theirs alone. */
    fun observeWords(memberId: String): Flow<Map<String, String>>

    fun newKinId(): String

    /**
     * Adds or edits a relative, signed by [byMemberId]; with [firstLink] or [photo], all go in one
     * batch. A null [photo] keeps the one they have.
     */
    suspend fun saveKin(kin: Kin, byMemberId: String, firstLink: KinLink? = null, photo: KinPhotoChange? = null)

    /** The server then removes their links and their photo. */
    suspend fun deleteKin(id: String)

    suspend fun addLink(link: KinLink, byMemberId: String)

    suspend fun removeLink(link: KinLink)

    suspend fun saveWords(memberId: String, words: Map<String, String>)
}
