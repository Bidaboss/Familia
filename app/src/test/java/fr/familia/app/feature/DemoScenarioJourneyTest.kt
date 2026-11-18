package fr.familia.app.feature

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.PresentationMode
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.auth.AuthRepository
import fr.familia.app.feature.auth.AuthUser
import fr.familia.app.feature.auth.GoogleTokenResult
import fr.familia.app.feature.auth.SessionUiState
import fr.familia.app.feature.auth.SessionViewModel
import fr.familia.app.feature.auth.SignInScreen
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.events.album.eventAlbumFolder
import fr.familia.app.feature.events.album.eventAlbumRights
import fr.familia.app.feature.events.detail.EventDetailScreen
import fr.familia.app.feature.events.detail.EventDetailUiState
import fr.familia.app.feature.events.detail.EventDetailViewModel
import fr.familia.app.feature.goodnews.FakeGoodNews
import fr.familia.app.feature.goodnews.FakeUploader
import fr.familia.app.feature.goodnews.edit.GoodNewsEditScreen
import fr.familia.app.feature.goodnews.edit.GoodNewsEditUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditViewModel
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedScreen
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedViewModel
import fr.familia.app.feature.home.HomeActions
import fr.familia.app.feature.home.HomeViewModel
import fr.familia.app.feature.presentation.PresentationBanner
import fr.familia.app.feature.presentation.PresentationSection
import fr.familia.app.feature.presentation.PresentationState
import fr.familia.app.feature.presentation.PresentationViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import fr.familia.app.feature.home.HomeR1bisActions
import fr.familia.app.feature.home.HomeR1bisScreen

/**
 * The other steps of the demo of 21 November (docs/scenario-demo.md), on the real screens and
 * ViewModels, with fakes for Firebase: the first sign-in of a new account up to the home screen,
 * « Je viens » on an event, the good news with a photo posted by the admin principal, the mode
 * présentation turned on then off, and the album of the meal with its slideshow. The gift steps
 * are in GiftsDemoJourneyTest. Fictional family only (TestFamily).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class DemoScenarioJourneyTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 21)
    private val admin = TestFamily.admin

    // --- 7. On installe ensemble: the first sign-in of a new account, up to the home screen.

    /** A member added the day before, with their Google address but no account yet. */
    private val newcomer = TestFamily.adult.copy(id = "new1", firstName = "New1", email = "new1@example.com", uid = null)

    /** Finds the newcomer by their address and remembers the account linked to them. */
    private class FirstSignInMembers(private val family: Family) : MemberRepository by FakeMembers(family) {
        val linked = mutableListOf<Pair<String, String>>()
        override suspend fun findByEmail(email: String): Member? = family.members.firstOrNull { it.email == email }
        override suspend fun linkAccount(memberId: String, uid: String) {
            linked += memberId to uid
        }
    }

    /** No account kept on the phone; Google answers with [next]. */
    private class FakeAuth(private val next: AuthUser) : AuthRepository {
        val tokens = mutableListOf<String>()
        override fun currentUser(): AuthUser? = null
        override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser {
            tokens += idToken
            return next
        }
        override fun signOut() = Unit
    }

    @Test
    fun `a new account signs in with Google and lands on its home screen`() {
        val family = TestFamily.family.copy(members = TestFamily.family.members + newcomer)
        val members = FirstSignInMembers(family)
        val auth = FakeAuth(AuthUser("uid-new1", "new1@example.com", emailVerified = true))
        // Today: the Accueil shows the day chosen, today at first.
        val events = FakeEvents(listOf(TestFamily.event("e1", admin, date = today)))
        val session = SessionViewModel(auth, members)
        idleUntil { session.uiState.value is SessionUiState.SignedOut }

        compose.setContent {
            FamiliaTheme {
                val state by session.uiState.collectAsStateWithLifecycle()
                val signedIn = state as? SessionUiState.SignedIn
                if (signedIn == null) {
                    SignInScreen(
                        state,
                        // The Google account picker cannot open in a test: it answers at once.
                        onSignInClick = {
                            session.onSignInStarted()
                            session.onGoogleTokenResult(GoogleTokenResult.Success("token-new1"))
                        },
                        onUseAnotherAccountClick = session::signOut,
                    )
                } else {
                    val home = remember(signedIn.member.id) {
                        HomeViewModel(members, events, signedIn.member.id, FakeRsvps(), today = flowOf(today))
                    }
                    val homeState by home.uiState.collectAsStateWithLifecycle()
                    HomeR1bisScreen(homeState, HomeActions(), HomeR1bisActions())
                }
            }
        }
        waitForText("Se connecter avec Google")
        compose.onNodeWithText("Se connecter avec Google").performClick()

        compose.waitUntil(TIMEOUT) { session.uiState.value is SessionUiState.SignedIn }
        assertEquals("new1", (session.uiState.value as SessionUiState.SignedIn).member.id)
        assertEquals(listOf("new1" to "uid-new1"), members.linked)
        assertEquals(listOf("token-new1"), auth.tokens)
        // The home screen of the newcomer shows the meal of the family.
        waitForText("Repas e1")
        assertNoText("Se connecter avec Google")
    }

    @Test
    fun `an address that is not in the family stays at the door`() {
        val members = FirstSignInMembers(TestFamily.family)
        val auth = FakeAuth(AuthUser("uid-x", "stranger@example.com", emailVerified = true))
        val session = SessionViewModel(auth, members)
        idleUntil { session.uiState.value is SessionUiState.SignedOut }
        session.onGoogleTokenResult(GoogleTokenResult.Success("token-x"))
        idleUntil { session.uiState.value is SessionUiState.NotAMember }
        assertEquals(emptyList<Pair<String, String>>(), members.linked)
    }

    // --- 2. Le Planning: « Je viens » in one tap.

    @Test
    fun `a member answers « Je viens » on the meal in one tap`() {
        val events = FakeEvents(listOf(TestFamily.event("e1", admin, date = today)))
        val rsvps = FakeRsvps()
        val vm = EventDetailViewModel(events, FakeMembers(), rsvps, "adult2", "e1", today = flowOf(today))
        compose.setContent {
            FamiliaTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                EventDetailScreen(state, onAnswer = vm::answer, onRetry = {}, onEdit = {}, onBack = {})
            }
        }
        compose.waitUntil(TIMEOUT) { vm.uiState.value is EventDetailUiState.Success }
        waitForText("Repas e1")
        compose.onAllNodesWithText("Je viens").onFirst().performClick()

        compose.waitUntil(TIMEOUT) { rsvps.answers.value["e1"].orEmpty().isNotEmpty() }
        assertEquals(listOf(Rsvp("adult2", RsvpStatus.Yes, "adult2")), rsvps.answers.value["e1"])
        // « Me le rappeler » (récit 56) stays behind its switch: nothing of it on this screen.
        assertNoText("Me le rappeler")
    }

    // --- 8. La photo du repas: the admin principal posts it, it shows once in the feed.

    @Test
    fun `the admin posts the photo of the meal and it shows once in the feed`() {
        val repo = FakeGoodNews()
        val uploader = FakeUploader()
        val feed = GoodNewsFeedViewModel(repo, FakeMembers(), admin.id)
        val edit = GoodNewsEditViewModel(repo, FakeMembers(), uploader, admin.id, null)
        idleUntil { edit.uiState.value is GoodNewsEditUiState.Editing }
        // The photo picker cannot open in a test: the photo of the table is given to the form.
        edit.setText("Joyeux anniversaire !")
        edit.addPhotos(listOf("content://repas"))

        compose.setContent {
            FamiliaTheme {
                val state by edit.uiState.collectAsStateWithLifecycle()
                if (state == GoodNewsEditUiState.Done) {
                    val feedState by feed.uiState.collectAsStateWithLifecycle()
                    GoodNewsFeedScreen(feedState, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
                } else {
                    GoodNewsEditScreen(
                        state,
                        onText = edit::setText,
                        onAddPhotos = edit::addPhotos,
                        onRemovePhoto = {},
                        onFor = {},
                        onMentions = {},
                        onSave = edit::save,
                        onDismissFailure = edit::dismissFailure,
                        onBack = {},
                        onCancelSending = edit::cancelSending,
                    )
                }
            }
        }
        waitForText("Publier pour toute la famille")
        // The button is under the photos and the pickers: scrolled to, as a finger would.
        compose.onNodeWithText("Publier pour toute la famille").performScrollTo().performClick()

        compose.waitUntil(TIMEOUT) { edit.uiState.value == GoodNewsEditUiState.Done }
        val news = repo.published.single()
        assertEquals(admin.id, news.authorId)
        assertEquals(listOf("content://repas"), uploader.sent.map { it.first })
        assertEquals(news.photoPath(news.photoIds.single()), uploader.sent.single().second)
        // Back on the feed, where it shows once.
        waitForText("Joyeux anniversaire !")
        assertEquals(1, compose.onAllNodesWithText("Joyeux anniversaire !").fetchSemanticsNodes().size)
    }

    // --- H − 10 min and « À la fin »: the mode présentation, on for 4 h, then off.

    /** config/presentation as the admin principal reads and writes it. */
    private class FakePresentationConfig : AppConfigRepository {
        val mode = MutableStateFlow<PresentationMode?>(null)
        var stops = 0
        override fun observeAppConfig(): Flow<AppConfig> = flowOf(AppConfig(null, null))
        override suspend fun fetchAppConfig(): AppConfig = AppConfig(null, null)
        override suspend fun setFeature(key: String, enabled: Boolean) = Unit
        override suspend fun setDiscoverOrder(keys: List<String>) = Unit
        override fun observePresentation(): Flow<PresentationMode?> = mode
        override suspend fun startPresentation(until: Instant?, now: Instant) {
            mode.value = PresentationMode(on = true, until = until)
        }
        override suspend fun stopPresentation() {
            stops++
            mode.value = PresentationMode(on = false, until = null)
        }
    }

    @Test
    fun `the mode présentation goes on for 4 h with its banner, then off`() {
        val now = Instant.parse("2026-11-21T11:20:00Z")
        val config = FakePresentationConfig()
        val vm = PresentationViewModel(config) { now }
        compose.setContent {
            FamiliaTheme {
                val mode by vm.mode.collectAsStateWithLifecycle()
                val state = PresentationState(mode, now, vm::start, vm::stop)
                Column {
                    PresentationBanner(state)
                    PresentationSection(state)
                }
            }
        }
        waitForText("4 h")
        assertNoText("Mode présentation jusqu")
        compose.onNodeWithText("4 h").performClick()

        waitForText("Mode présentation jusqu", substring = true)
        assertEquals(PresentationMode(on = true, until = Instant.parse("2026-11-21T15:20:00Z")), config.mode.value)
        assertNoText("4 h")

        // « Éteindre » is on the banner and in the card; the banner's is the first.
        compose.onAllNodesWithText("Éteindre").onFirst().performClick()
        waitForText("4 h")
        assertEquals(1, config.stops)
        assertNoText("Mode présentation jusqu")
        assertNoText("Éteindre")
    }

    // --- Les photos: the album of the meal, then « Lancer le diaporama ».

    private class FakeAlbum(initial: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> =
            photos.map { list -> list.filter { it.folder == folder }.sortedWith(albumPhotoOrder) }
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) {
            photos.value = photos.value + AlbumPhoto(folder, me.id, me.firstName, slot, createdAt = null)
        }
        override suspend fun deletePhoto(photo: AlbumPhoto) {
            photos.value = photos.value.filterNot { it.id == photo.id }
        }
    }

    @Test
    fun `the album of the meal opens on its day, takes the photo of the table and plays the slideshow`() {
        val folder = eventAlbumFolder("e1")
        val events = FakeEvents(listOf(TestFamily.event("e1", admin, date = today)))
        val rsvps = FakeRsvps()
        rsvps.answers.value = mapOf("e1" to listOf(Rsvp(admin.id, RsvpStatus.Yes, admin.id)))
        val album = FakeAlbum(
            listOf(
                AlbumPhoto(folder, "adult1", "Adult1", 1, Instant.parse("2026-11-21T11:00:00Z")),
                AlbumPhoto(folder, "adult2", "Adult2", 1, Instant.parse("2026-11-21T11:05:00Z")),
            ),
        )
        val uploader = FakeUploader()
        val rights = eventAlbumRights(events, FakeMembers(), rsvps, "e1", admin.id, flowOf(today))
        val vm = AlbumPhotosViewModel(album, rights, uploader, null, admin.id, folder)
        compose.setContent {
            FamiliaTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                AlbumScreen("Photos", state, rememberPhotoActions(vm), onBack = {})
            }
        }
        // The admin said « je viens » and it is the day: the album takes photos.
        waitForText("Ajouter des photos")
        compose.waitUntil(TIMEOUT) { vm.uiState.value.canAdd }
        vm.addPhotos(listOf("content://table"))
        compose.waitUntil(TIMEOUT) { album.photos.value.size == 3 }
        assertEquals(listOf("content://table"), uploader.sent.map { it.first })
        assertEquals(Triple(admin.id, "Admin1", 1), album.photos.value.last().let { Triple(it.authorId, it.authorName, it.slot) })
        assertNull(vm.uiState.value.problem)

        // The slideshow: a photo every 4 seconds, oldest first. The clock is driven by hand, as it never rests.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Lancer le diaporama").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("1 sur 3").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("2 sur 3").assertExists()
        compose.onNodeWithText("Fermer").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0, compose.onAllNodesWithText("sur 3", substring = true).fetchSemanticsNodes().size)
        compose.mainClock.autoAdvance = true
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertNoText(text: String) {
        compose.waitForIdle()
        assertEquals("« $text » must not show", 0, compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)
    }

    /** Before any screen is shown: runs the main looper until [done]. */
    private fun idleUntil(done: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) fail("not reached in time")
            Thread.sleep(5)
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
