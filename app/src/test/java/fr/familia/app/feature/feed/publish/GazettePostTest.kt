package fr.familia.app.feature.feed.publish

import fr.familia.app.core.data.GazettePostRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.PostPlace
import fr.familia.app.core.model.canRemoveGazettePost
import fr.familia.app.core.model.gazetteMentionCandidates
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.feed.postsForFeed
import fr.familia.app.feature.feed.timeLeft
import fr.familia.app.feature.joinme.FakeLocator
import fr.familia.app.feature.goodnews.FakeUploader
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * « Publier dans la gazette » (récits 117, 119 et 121). TestFamily: admin1 (admin principal, h1),
 * adult1 (h1), adult2 (h2), kid1 (child with the app, parent adult1), kid2 (child without the app).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GazettePostTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val family = TestFamily.family

    private class FakePosts(initial: List<GazettePost> = emptyList()) : GazettePostRepository {
        val posts = MutableStateFlow(initial)
        val published = mutableListOf<GazettePost>()
        val edits = mutableListOf<Triple<String, String, List<String>>>()
        private var next = 0
        override fun observeSince(since: Instant, limit: Int): Flow<List<GazettePost>> = posts
        override fun observePost(id: String): Flow<GazettePost?> = posts.map { list -> list.firstOrNull { it.id == id } }
        override fun newId(): String = "id${next++}"
        override suspend fun publish(post: GazettePost) { published += post }
        override suspend fun edit(id: String, text: String, mentionIds: List<String>) { edits += Triple(id, text, mentionIds) }
        override suspend fun remove(id: String) { posts.value = posts.value.filter { it.id != id } }
        val stopped = mutableListOf<Pair<String, String>>()
        override suspend fun stopSharing(id: String, label: String) { stopped += id to label }
    }

    private fun post(id: String, author: String, at: Long = 0) = GazettePost(id, author, author, "Coucou", createdAt = Instant.ofEpochSecond(at))

    @Test
    fun `its author, a responsible adult of its author and the admin principal remove it (récit 121)`() {
        val byKid = post("p1", "kid1")
        assertTrue(canRemoveGazettePost(byKid, TestFamily.kid, family))
        assertTrue(canRemoveGazettePost(byKid, TestFamily.adult, family))
        assertTrue(canRemoveGazettePost(byKid, TestFamily.admin, family))
        assertFalse(canRemoveGazettePost(byKid, TestFamily.otherAdult, family))
        val byAdult = post("p2", "adult2")
        assertFalse(canRemoveGazettePost(byAdult, TestFamily.adult, family))
        assertFalse(canRemoveGazettePost(byAdult, TestFamily.kid, family))
    }

    @Test
    fun `the others with the app can be named, children with it too, never one without it (PRD 119 c)`() {
        assertEquals(listOf("admin1", "adult2", "kid1"), gazetteMentionCandidates(TestFamily.adult, family).map { it.id })
        assertEquals(listOf("admin1", "adult1", "adult2"), gazetteMentionCandidates(TestFamily.kid, family).map { it.id })
    }

    @Test
    fun `a word, a photo or both, 1 000 characters and 6 photos at most (PRD 119 b)`() {
        assertEquals(setOf(PostFormError.Empty), PostForm(text = "  ").validate())
        assertTrue(PostForm(text = "Coucou").validate().isEmpty())
        assertTrue(PostForm(photos = listOf("content://a")).validate().isEmpty())
        assertEquals(setOf(PostFormError.TextTooLong), PostForm(text = "x".repeat(1001)).validate())
        val six = PostForm().withPhotos((1..4).map { "content://$it" }).withPhotos((3..9).map { "content://$it" })
        assertEquals((1..6).map { "content://$it" }, six.photos)
    }

    @Test
    fun `the gazette shows the publications of its window, newest first`() {
        val list = listOf(post("old", "adult1", 10), post("new", "adult1", 300), post("mid", "adult2", 200), GazettePost("pending", "adult1", "a", "x"))
        assertEquals(listOf("new", "mid"), postsForFeed(list, Instant.ofEpochSecond(100), 10).map { it.id })
        assertEquals(listOf("new"), postsForFeed(list, Instant.ofEpochSecond(100), 1).map { it.id })
    }

    @Test
    fun `publishing sends the photos first, then the publication, with only who may be named`() = runTest {
        val posts = FakePosts()
        val uploader = FakeUploader()
        val vm = PostEditViewModel(posts, FakeMembers(), uploader, "adult1", null)
        advanceUntilIdle()
        vm.setText("Belle journée avec @Kid1 et @Kid2")
        vm.setMentions(setOf("kid1", "kid2"))
        vm.addPhotos(listOf("content://a"))
        vm.save()
        advanceUntilIdle()
        assertEquals(PostEditUiState.Done, vm.uiState.value)
        val sent = posts.published.single()
        assertEquals("adult1", sent.authorId)
        assertEquals(listOf("kid1"), sent.mentionIds)
        assertEquals(1, sent.photoIds.size)
        assertEquals("gazettePosts/adult1/${sent.id}/${sent.photoIds[0]}.jpg", uploader.sent.single().second)
    }

    @Test
    fun `an empty publication is refused, a word alone goes without any photo`() = runTest {
        val posts = FakePosts()
        val vm = PostEditViewModel(posts, FakeMembers(), FakeUploader(), "adult1", null)
        advanceUntilIdle()
        vm.save()
        assertEquals(setOf(PostFormError.Empty), (vm.uiState.value as PostEditUiState.Editing).errors)
        vm.setText("Un mot")
        vm.save()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), posts.published.single().photoIds)
    }

    @Test
    fun `only its author changes the text, the photos stay`() = runTest {
        val posts = FakePosts(listOf(post("p1", "kid1").copy(photoIds = listOf("a"))))
        val parent = PostEditViewModel(posts, FakeMembers(), FakeUploader(), "adult1", "p1")
        advanceUntilIdle()
        assertEquals(PostEditUiState.Unavailable, parent.uiState.value)
        val author = PostEditViewModel(posts, FakeMembers(), FakeUploader(), "kid1", "p1")
        advanceUntilIdle()
        author.setText("")
        author.save()
        advanceUntilIdle()
        assertEquals(Triple("p1", "", emptyList<String>()), posts.edits.single())
    }

    @Test
    fun `the chef de famille of the child removes it too, not the chef of another one (règle du 25 11)`() {
        fun m(id: String, household: String, admin: Boolean = false, profile: MemberProfile = MemberProfile.Adult, parents: List<String> = emptyList()) =
            Member(id, id, "$id@example.com", null, household, profile, admin, parents, "uid-$id")
        val owner = m("owner", "h0", admin = true)
        val chef = m("chef", "h1", admin = true)
        val otherChef = m("chef2", "h2", admin = true)
        val parent = m("parent", "h1")
        val child = m("child", "h1", profile = MemberProfile.ChildWithApp, parents = listOf("parent"))
        val family = Family(emptyList(), listOf(owner, chef, otherChef, parent, child), ownerId = "owner")
        val byChild = post("p", "child")
        assertTrue(canRemoveGazettePost(byChild, chef, family))
        assertTrue(canRemoveGazettePost(byChild, parent, family))
        assertTrue(canRemoveGazettePost(byChild, owner, family))
        assertFalse(canRemoveGazettePost(byChild, otherChef, family))
        // A chef never removes the publication of another adult of the household.
        assertFalse(canRemoveGazettePost(post("q", "parent"), chef, family))
    }

    @Test
    fun `a position alone is a publication, shared 2 hours (récit 117)`() {
        val here = PostPlace("Annecy · Haute-Savoie", 45.9, 6.12)
        assertTrue(PostForm(place = here).validate().isEmpty())
        val at = Instant.ofEpochSecond(1_000)
        val shared = post("p", "adult1").copy(createdAt = at, place = here)
        assertEquals(at.plus(Duration.ofHours(2)), shared.shareEndsAt)
        assertTrue(shared.isSharing(at.plus(Duration.ofMinutes(119))))
        assertFalse(shared.isSharing(at.plus(Duration.ofHours(2))))
        // Once stopped, only the name stays: no point, nothing shared.
        val stopped = shared.copy(place = PostPlace(here.label))
        assertEquals(null, stopped.shareEndsAt)
        assertFalse(stopped.isSharing(at))
    }

    @Test
    fun `the time left reads 1 h 10, 1 h, 45 min, and never 0 min`() {
        assertEquals("1 h 10", timeLeft(Duration.ofMinutes(70)))
        assertEquals("1 h", timeLeft(Duration.ofMinutes(60)))
        assertEquals("45 min", timeLeft(Duration.ofMinutes(45)))
        assertEquals("1 min", timeLeft(Duration.ofSeconds(5)))
    }

    @Test
    fun `« Dire où je suis » puts the town of the moment, or « Ma position », and sends the point`() = runTest {
        val posts = FakePosts()
        val locator = FakeLocator(position = 45.9 to 6.12, addresses = mapOf((45.9 to 6.12) to "Annecy · Haute-Savoie"))
        val vm = PostEditViewModel(posts, FakeMembers(), FakeUploader(), "adult1", null, locator = locator)
        advanceUntilIdle()
        vm.locate()
        advanceUntilIdle()
        assertEquals(PostPlace("Annecy · Haute-Savoie", 45.9, 6.12), (vm.uiState.value as PostEditUiState.Editing).form.place)
        vm.save()
        advanceUntilIdle()
        assertEquals(PostPlace("Annecy · Haute-Savoie", 45.9, 6.12), posts.published.single().place)

        locator.position = 1.0 to 2.0
        val unnamed = PostEditViewModel(FakePosts(), FakeMembers(), FakeUploader(), "adult1", null, locator = locator)
        advanceUntilIdle()
        unnamed.locate()
        advanceUntilIdle()
        assertEquals("Ma position", (unnamed.uiState.value as PostEditUiState.Editing).form.place?.label)
    }

    @Test
    fun `no position, or the permission refused, says so and publishes nothing`() = runTest {
        val vm = PostEditViewModel(FakePosts(), FakeMembers(), FakeUploader(), "adult1", null, locator = FakeLocator(position = null))
        advanceUntilIdle()
        vm.locate()
        advanceUntilIdle()
        val editing = vm.uiState.value as PostEditUiState.Editing
        assertEquals(PlaceProblem.NoPosition, editing.placeProblem)
        assertEquals(null, editing.form.place)
        vm.locationRefused()
        assertEquals(PlaceProblem.NoPermission, (vm.uiState.value as PostEditUiState.Editing).placeProblem)
        assertFalse((vm.uiState.value as PostEditUiState.Editing).canPublish)
    }
}
