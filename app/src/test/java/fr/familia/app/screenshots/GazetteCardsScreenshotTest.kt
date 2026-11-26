package fr.familia.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.data.GazetteLastComment
import fr.familia.app.core.data.GazetteThread
import fr.familia.app.core.data.GazetteThreadRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.feed.AlbumFeedCard
import fr.familia.app.feature.feed.EventFeedCard
import fr.familia.app.feature.feed.FeedAlbum
import fr.familia.app.feature.feed.FeedContext
import fr.familia.app.feature.feed.FeedList
import fr.familia.app.feature.feed.FeedSocialEnv
import fr.familia.app.feature.feed.FeedState
import fr.familia.app.feature.feed.LocalFeedGlass
import fr.familia.app.feature.feed.PostFeedCard
import fr.familia.app.core.theme.PaperTheme
import fr.familia.app.feature.feed.FeedCardDetail
import fr.familia.app.feature.feed.LocalFeedSocialEnv
import fr.familia.app.feature.feed.publish.PostEditScreen
import fr.familia.app.feature.feed.publish.PostEditUiState
import fr.familia.app.feature.feed.publish.PostForm
import fr.familia.app.feature.feed.publish.SheetCream
import fr.familia.app.feature.yearago.MemoryAlbum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Les cartes de la gazette mises au prototype (retour du 26/11): the top on two lines, the day mark
 * in its orange pill, « ♡ 2 J'aime », « 💬 3 Commenter », « Qui a aimé ? » and the 2 last comments.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class GazetteCardsScreenshotTest {

    private val adult = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    private val other = Member("b", "Noé", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    private val family = Family(listOf(Household("h1", "Foyer Un")), listOf(adult, other))
    private val today = LocalDate.of(2026, 11, 18)

    private fun feed(): FeedState {
        val event = Event(
            "e1", "Repas de famille", today.plusDays(3), LocalTime.of(12, 30), "Chez Inès", null,
            EventTarget.ForFamily, "b", "Noé", createdAt = Instant.parse("2026-11-18T08:40:00Z"),
        )
        val post = GazettePost("p1", "a", "Camille", "Balade au bord du lac", createdAt = Instant.parse("2026-11-17T07:12:00Z"))
        // Les photos d'un événement, comme une publication de photo (§11).
        val album = FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Event, "e0", "Repas de fête"), "events/e0", today)
        val photo = AlbumPhoto("events/e0", "b", "Noé", 1, Instant.parse("2026-11-18T09:04:00Z"))
        return FeedState(
            cards = listOf(
                EventFeedCard(event, canAnswer = true, rsvps = null, comments = null),
                AlbumFeedCard(album, today, listOf(photo)),
                PostFeedCard(post, posts = null),
            ),
            context = FeedContext(adult, family, today, false),
            unseen = 0,
            canLoadMore = true,
            onMore = {},
            onSeen = {},
            social = FeedSocialEnv(Threads, null, adult, false),
        )
    }

    private object Threads : GazetteThreadRepository {
        override fun observe(threadId: String): Flow<GazetteThread> = flowOf(
            GazetteThread(
                reactions = mapOf("a" to "💛", "b" to "🎉"),
                commentCount = 3,
                last = listOf(
                    GazetteLastComment("c1", "b", "Noé", "Trop beau !", null),
                    GazetteLastComment("c2", "a", "Camille", "Merci tout le monde", null),
                ),
            ),
        )

        override suspend fun react(threadId: String, memberId: String, reaction: String?) = Unit
    }

    @Test
    fun gazetteCardsOnPhoto() = capture("gazette_cartes_prototype") {
        // A dark gradient stands for the photo of the theme.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A5A4A), Color(0xFF2E3A30))))) {
            CompositionLocalProvider(LocalFeedGlass provides true) { FeedList(feed(), onPublish = {}) }
        }
    }

    @Test
    fun gazetteCardsOnCream() = capture("gazette_cartes_creme") { FeedList(feed(), onPublish = {}) }

    @Test
    fun publishSheet() = capture("gazette_publier_feuille") {
        // The content of the sheet on its cream, as it rises from the bottom (retour du 26/11).
        Box(Modifier.fillMaxSize().background(SheetCream)) {
            PaperTheme {
                PostEditScreen(
                    PostEditUiState.Editing(PostForm(), null, adult, listOf(other)),
                    {}, {}, {}, {}, {}, {}, {}, onClose = {},
                )
            }
        }
    }

    @Test
    fun photoDetailOnBlack() = capture("gazette_detail_photo_noir") {
        val post = GazettePost(
            "p2", "a", "Camille", "Balade au bord du lac", photoIds = listOf("x1", "x2", "x3"),
            createdAt = Instant.parse("2026-11-17T07:12:00Z"),
        )
        val card = PostFeedCard(post, posts = null)
        val env = FeedSocialEnv(Threads, null, adult, false)
        CompositionLocalProvider(LocalFeedSocialEnv provides env) {
            FeedCardDetail(card, card.social, FeedContext(adult, family, today, false), env, onClose = {})
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") { FamiliaTheme(darkTheme = false) { content() } }
    }
}
