package fr.familia.app.core.data

import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import kotlinx.coroutines.flow.Flow

/** Access to the question of the week (récit 37). Screens never talk to Firestore directly. */
interface WeeklyQuestionRepository {
    /** The [count] latest questions, newest first, updated live. */
    fun observeLatest(count: Int): Flow<List<WeeklyQuestion>>

    /** One question, updated live; null when it does not exist. */
    fun observeQuestion(id: String): Flow<WeeklyQuestion?>

    /** The answer of [memberId], or null before they answer or pass. */
    fun observeMyAnswer(questionId: String, memberId: String): Flow<WeeklyAnswer?>

    /**
     * Everyone's answers. The rules allow it only once the member answered or passed, or from
     * revealAt: call it only then.
     */
    fun observeAnswers(questionId: String): Flow<List<WeeklyAnswer>>

    /**
     * Answers for [memberId], or « Passer » when [text] is null. Replaces an earlier answer.
     * [photoId] names a photo already sent to Storage.
     */
    suspend fun answer(questionId: String, memberId: String, memberName: String, text: String?, photoId: String? = null)

    /** A fresh id for a photo (letters and digits, as the rules expect). */
    fun newPhotoId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(20)

    suspend fun removeAnswer(questionId: String, memberId: String)
}
