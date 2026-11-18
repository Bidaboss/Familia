package fr.familia.app.core.welcome

import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeTest {

    private fun member(id: String, name: String, household: String) =
        Member(id, name, null, null, household, MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)

    private val camille = member("m1", "Camille", "h2")
    private val ines = member("m2", "Inès", "h1")
    private val sam = member("m3", "Sam", "h2")
    private val noe = member("m4", "Noé", "h1")
    private val family = Family(listOf(Household("h1", "Foyer Un"), Household("h2", "Foyer Deux")), listOf(camille, ines, sam, noe), ownerId = "m1")

    private fun config(on: Boolean) = AppConfig(null, null, features = if (on) mapOf("openingAnimation" to true) else emptyMap())

    @Test
    fun `there is one fruit per member, my household first`() {
        val fruits = welcomeFruits(family, ines)
        assertEquals(listOf("m2", "m4", "m1", "m3"), fruits.map { it.memberId })
        assertEquals(listOf(0, 1, 2, 3), fruits.map { it.place })
    }

    @Test
    fun `the fruits of the tree are taken clockwise from the top`() {
        val angles = TREE_SLOTS.map { clockwiseFromTop(it.center) }
        assertEquals(angles.sorted(), angles)
        assertEquals(13, TREE_SLOTS.size)
        // The fruit at the very top of the tree goes first.
        assertEquals(25.5f, TREE_SLOTS.first().center.y, 0f)
    }

    @Test
    fun `past the 13 fruits of the tree, a member's fruit comes out of the sun`() {
        val many = (1..15).map { member("x$it", "Prénom$it", "h1") }
        val fruits = welcomeFruits(Family(listOf(Household("h1", "Foyer Un")), many), many.first())
        assertEquals(15, fruits.size)
        assertTrue(fruits.take(13).all { it.tree != null })
        assertNull(fruits[13].tree)
        assertNull(fruits[14].tree)
    }

    @Test
    fun `shared first letters give two letters in the fruit`() {
        val lou = member("m5", "Lou", "h1")
        val lea = member("m6", "Léna", "h1")
        val fruits = welcomeFruits(Family(listOf(Household("h1", "Foyer Un")), listOf(lou, lea)), lou)
        assertEquals(setOf("Lo", "Lé"), fruits.map { it.initials }.toSet())
    }

    @Test
    fun `the welcome plays when its module is on for the family`() = runTest {
        val choice = chooseWelcome(flowOf(family), flowOf(config(on = true)), ines)
        assertTrue(choice is WelcomeChoice.Play)
    }

    @Test
    fun `the welcome stays away while its module is in test, except for the admin principal`() = runTest {
        assertEquals(WelcomeChoice.Off, chooseWelcome(flowOf(family), flowOf(config(on = false)), ines))
        assertNotNull(chooseWelcome(flowOf(family), flowOf(config(on = false)), camille) as? WelcomeChoice.Play)
    }

    @Test
    fun `offline the app goes on, and the welcome waits for the next opening`() = runTest {
        val never = flow<AppConfig> { awaitCancellation() }
        assertEquals(WelcomeChoice.Skip, chooseWelcome(flowOf(family), never, ines))
    }

    @Test
    fun `a family read that fails does not block the opening`() = runTest {
        val broken = flow<Family> { throw IllegalStateException("hors ligne") }
        assertEquals(WelcomeChoice.Skip, chooseWelcome(broken, flowOf(config(on = true)), ines))
    }

    @Test
    fun `once seen off, the opening waits half a second at most for the modules`() = runTest {
        val never = flow<AppConfig> { awaitCancellation() }
        val started = testScheduler.currentTime
        assertEquals(WelcomeChoice.Skip, chooseWelcome(flowOf(family), never, ines, WELCOME_OFF_WAIT_MILLIS))
        assertEquals(WELCOME_OFF_WAIT_MILLIS, testScheduler.currentTime - started)
    }

    @Test
    fun `the long opening, the welcome, is the default, and an unknown choice goes back to it`() {
        assertEquals(OpeningStyle.Long, OpeningStyle.of(null))
        assertEquals(OpeningStyle.Long, OpeningStyle.of("autre"))
        OpeningStyle.entries.forEach { assertEquals(it, OpeningStyle.of(it.key)) }
    }

    @Test
    fun `the final olive tree has 13 fruits of the same size and well formed leaves`() {
        assertEquals(13, TREE_FRUITS.size)
        assertTrue(TREE_FRUITS.all { it.radius == 4.9f })
        assertEquals(0, OLIVE_LEAVES.size % 6)
        // 258 olive leaves, then the small leaf of each of the 13 fruits.
        assertEquals(271, OLIVE_LEAVES.size / 6)
        for (i in 0 until OLIVE_LEAVES.size / 6) {
            assertTrue(OLIVE_LEAVES[i * 6 + 5].toInt() in OLIVE_LEAF_COLORS.indices)
            assertTrue(OLIVE_LEAVES[i * 6 + 2] > 0f && OLIVE_LEAVES[i * 6 + 3] > 0f)
        }
    }

    @Test
    fun `the thin red ring sits on the edge of the sun`() {
        assertTrue(OLIVE_RING_RADIUS < OLIVE_SUN_RADIUS)
        assertTrue(OLIVE_RING_RADIUS + OLIVE_RING_WIDTH / 2 > OLIVE_SUN_RADIUS)
    }
}
