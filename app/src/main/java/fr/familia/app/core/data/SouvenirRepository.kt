package fr.familia.app.core.data

import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import kotlinx.coroutines.flow.Flow

/** Souvenirs (récit 34). The server gives them; the app only shows or hides them. */
interface SouvenirRepository {
    /** All of the member's own souvenirs, shown or not. */
    fun observeMine(memberId: String): Flow<List<Souvenir>>

    /** The souvenirs of [memberId] seen by the family: all of them since récit 114 (« Réussites »). */
    fun observeShown(memberId: String): Flow<List<Souvenir>>

    /** The member's own counters, for « Encore 5 coups de main ». */
    fun observeCounters(memberId: String): Flow<SouvenirCounters>

    /** Does nothing since récit 114: the « Réussites » are public. */
    suspend fun setShown(memberId: String, key: String, shown: Boolean)
}
