package fr.familia.app.core.data

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import kotlinx.coroutines.flow.Flow

/** Access to the « Bonnes nouvelles ». Screens never talk to Firestore directly. */
interface GoodNewsRepository {
    /** The [count] most recent, newest first, updated live (offline cache too). */
    fun observeLatest(count: Int): Flow<List<GoodNews>>

    /** One good news, updated live; null once removed or malformed. */
    fun observePost(id: String): Flow<GoodNews?>

    /** A fresh id for a good news or a photo not saved yet. */
    fun newId(): String

    /** Writes a new good news, once its photos are in Storage. */
    suspend fun publish(news: GoodNews)

    /** The author changes the text and the members named. */
    suspend fun edit(id: String, text: String, mentionIds: List<String>)

    /** Sets, changes or removes (null) the reaction of [memberId]. */
    suspend fun react(id: String, memberId: String, reaction: Reaction?)

    /** « Masquer pour moi » ([hidden] true), or show it again, for [memberId] only. */
    suspend fun setHidden(id: String, memberId: String, hidden: Boolean)

    suspend fun remove(id: String)
}
