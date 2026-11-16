package fr.familia.app.core.data

import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItPost
import kotlinx.coroutines.flow.Flow

/** « Qui est-ce ? » (PRD, récit 43). See docs/data-model.md. */
interface WhoIsItRepository {
    /** The latest photos, newest first, updated live. */
    fun observeLatest(count: Int): Flow<List<WhoIsItPost>>

    /** One photo; null once removed. */
    fun observePost(id: String): Flow<WhoIsItPost?>

    /** The answer; null while it cannot be read yet (not guessed) or not written. */
    fun observeAnswer(id: String): Flow<WhoIsItAnswer?>

    /** Every guess, once one may read them (the author, or after guessing); else empty. */
    fun observeGuesses(id: String): Flow<List<WhoIsItGuess>>

    fun newPostId(): String

    /** Writes the photo and its answer, once the files are in Storage. Server time set here. */
    suspend fun publish(post: WhoIsItPost, answer: WhoIsItAnswer)

    /** The author changes the answer, for example to link someone of the tree. */
    suspend fun setAnswer(id: String, answer: WhoIsItAnswer)

    /** One guess of [guess.memberId], never changed afterwards. */
    suspend fun guess(id: String, guess: WhoIsItGuess)

    suspend fun delete(id: String)
}
