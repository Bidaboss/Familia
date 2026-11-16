package fr.familia.app.feature.joinme

import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.place.Place
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** « Rejoignez-moi » (récit 33) with the fictional family of the tests: adult1 in h1. */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinMeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun editing(vm: JoinMeViewModel) = vm.uiState.value as JoinMeUiState.Editing

    @Test
    fun `nobody is ticked at first, the shortcuts tick my household or the family, never me nor a child without the app`() = runTest {
        val vm = JoinMeViewModel(FakeMembers(), FakeJoinMe(), FakeLocator(), "adult1")
        val state = editing(vm)
        assertTrue(state.recipientIds.isEmpty())
        assertEquals(listOf("Foyer Un", "Foyer Deux"), state.groups.map { it.name })
        assertEquals(listOf("admin1", "kid1"), state.groups[0].members.map { it.id })
        assertEquals(listOf("adult2"), state.groups[1].members.map { it.id })
        val (household, family) = state.shortcuts
        assertEquals(setOf("admin1", "kid1"), household.memberIds)
        assertEquals(setOf("admin1", "kid1", "adult2"), family.memberIds)
        vm.applyShortcut(household)
        assertEquals(setOf("admin1", "kid1"), editing(vm).recipientIds)
        vm.applyShortcut(household)
        assertTrue(editing(vm).recipientIds.isEmpty())
    }

    @Test
    fun `the position is read once, with its address, and sending waits for someone ticked`() = runTest {
        val repo = FakeJoinMe()
        val vm = JoinMeViewModel(FakeMembers(), repo, FakeLocator(), "adult1")
        assertFalse(editing(vm).canSend)
        vm.locate()
        assertEquals(Place(46.4915, -1.7931, address = "1 promenade de la Mer, 85100 Les Sables"), editing(vm).place)
        assertFalse(editing(vm).canSend)
        vm.toggle("adult2")
        vm.changeName("  Plage des Sables ")
        vm.changeNote("   ")
        assertTrue(editing(vm).canSend)
        vm.send()
        assertEquals(JoinMeUiState.Sent, vm.uiState.value)
        val sent = repo.shares.value.single()
        assertEquals(listOf("adult2"), sent.recipientIds)
        assertEquals("Plage des Sables", sent.place.name)
        assertEquals(null, sent.note)
        assertEquals("Adult1", sent.authorName)
    }

    @Test
    fun `no position, no permission or nothing found is said, and nothing can be sent`() = runTest {
        val vm = JoinMeViewModel(FakeMembers(), FakeJoinMe(), FakeLocator(position = null), "adult1")
        vm.toggle("adult2")
        vm.locate()
        assertEquals(PlaceLookup.NoPosition, editing(vm).lookup)
        vm.permissionDenied()
        assertEquals(PlaceLookup.NoPermission, editing(vm).lookup)
        vm.chooseSource(PlaceSource.Elsewhere)
        vm.changeQuery("Nulle part")
        vm.search()
        assertEquals(PlaceLookup.NotFound, editing(vm).lookup)
        assertFalse(editing(vm).canSend)
    }

    @Test
    fun `an address is looked up, a Maps link gives its pin and fills the name`() = runTest {
        val vm = JoinMeViewModel(FakeMembers(), FakeJoinMe(), FakeLocator(), "adult1")
        vm.chooseSource(PlaceSource.Elsewhere)
        vm.changeQuery("12 rue des Lilas")
        vm.search()
        assertEquals("12 rue des Lilas, 44000 Nantes", editing(vm).place?.address)
        vm.changeQuery("https://www.google.com/maps/place/Plage+des+Sables/@46.4,-1.7,17z/data=!3d46.4915!4d-1.7931")
        vm.search()
        assertEquals(Place(46.4915, -1.7931, address = "1 promenade de la Mer, 85100 Les Sables"), editing(vm).place)
        assertEquals("Plage des Sables", editing(vm).name)
    }

    @Test
    fun `a short link is opened once, then read`() = runTest {
        val short = "https://maps.app.goo.gl/AbCd"
        val locator = FakeLocator(shortLinks = mapOf(short to "https://www.google.com/maps/@46.4915,-1.7931,15z"))
        val vm = JoinMeViewModel(FakeMembers(), FakeJoinMe(), locator, "adult1")
        vm.chooseSource(PlaceSource.Elsewhere)
        vm.changeQuery("Viens $short")
        vm.search()
        assertEquals(46.4915, editing(vm).place?.lat)
        vm.changeQuery("https://maps.app.goo.gl/Inconnu")
        vm.search()
        assertEquals(PlaceLookup.NotFound, editing(vm).lookup)
    }

    @Test
    fun `texts too long block the send, a failed send says so and keeps the form`() = runTest {
        val vm = JoinMeViewModel(FakeMembers(), FakeJoinMe(failWith = IOException("hors ligne")), FakeLocator(), "adult1")
        vm.locate()
        vm.toggle("adult2")
        vm.changeName("x".repeat(Place.MAX_NAME + 1))
        assertEquals(setOf(JoinMeError.NameTooLong), editing(vm).errors)
        assertFalse(editing(vm).canSend)
        vm.changeName("")
        vm.changeNote("x".repeat(JoinMe.MAX_NOTE + 1))
        assertFalse(editing(vm).canSend)
        vm.changeNote("À tout de suite")
        vm.send()
        assertTrue(editing(vm).sendFailed)
        assertFalse(editing(vm).sending)
    }

    @Test
    fun `the home card hides what expired and stops sharing`() = runTest {
        val now = Instant.parse("2026-11-21T16:00:00Z")
        val repo = FakeJoinMe(now)
        val ticks = MutableStateFlow(now)
        val home = JoinMeHomeViewModel(repo, "adult2", clock = { ticks.value }, ticks = ticks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.uiState.collect {} }
        repo.send("adult1", "Adult1", listOf("adult2"), Place(46.5, -1.8), null)
        repo.send("adult2", "Adult2", listOf("adult1"), Place(46.6, -1.9), null)
        var ready = home.uiState.value as JoinMeHomeUiState.Ready
        assertEquals(listOf("adult1"), ready.received.map { it.authorId })
        assertEquals(listOf("adult2"), ready.sent.map { it.authorId })
        home.stop(ready.sent.single().id)
        ready = home.uiState.value as JoinMeHomeUiState.Ready
        assertTrue(ready.sent.isEmpty())
        ticks.value = now.plus(JoinMe.LIFETIME)
        ready = home.uiState.value as JoinMeHomeUiState.Ready
        assertTrue(ready.received.isEmpty())
    }
}
