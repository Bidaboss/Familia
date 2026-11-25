package fr.familia.app.feature.feed

import fr.familia.app.core.data.gazetteThreadId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.WeeklyQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * « Question de la semaine » (récit 67): the questions published in the window, with their number
 * of answers only (no name, récit 37). « Répondre » until I answered or passed, then « Voir les
 * réponses ». Two questions at most: one a week, and the window is two weeks.
 */
class WeeklyFeedSource(private val questions: WeeklyQuestionRepository, private val meId: String) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        questions.observeLatest(2 * window.pages).map { list -> weeklyForFeed(list, window.since).map { WeeklyFeedCard(it, questions, meId) } }
}

/** The questions of the fil: published since [since]. */
fun weeklyForFeed(list: List<WeeklyQuestion>, since: Instant): List<WeeklyQuestion> = list.filter { !it.publishedAt.isBefore(since) }

class WeeklyFeedCard(
    val question: WeeklyQuestion,
    private val questions: WeeklyQuestionRepository?,
    private val meId: String,
) : FeedCard {
    override val key: String = "weekly:${question.id}"

    /** J'aime and comments on its thread (récit 115). */
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key))
    override val at: Instant = question.publishedAt

    /** Familia asks it: it counts in the badge for everyone. */
    override val authorId: String? = null

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Peach
        val answered = remember(question.id, questions) {
            questions?.observeMyAnswer(question.id, meId)?.map { it != null }?.catch { emit(false) }
        }?.collectAsStateWithLifecycle(initialValue = false)
        FeedCardShell(tone = tone, onOpen = navigation.onOpenWeeklyQuestion) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    val label = stringResource(R.string.feed_label_weekly).uppercase()
                    val count = pluralStringResource(R.plurals.weekly_answer_count, question.answerCount, question.answerCount)
                    Text(
                        "$label · $count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tone.label,
                    )
                    Text(
                        question.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val done = answered?.value == true
                if (!context.readOnly || done) {
                    FilledTonalButton(onClick = navigation.onOpenWeeklyQuestion, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (done || context.readOnly) R.string.weekly_see_answers else R.string.feed_weekly_answer))
                    }
                }
            }
        }
    }
}
