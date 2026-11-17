package fr.familia.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A double tap opens one screen, not two (démo du 21): the guard over the NavHost of FamiliaApp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapGuardTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            Box(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = "list", modifier = Modifier.fillMaxSize()) {
                    composable("list") { Button(onClick = { nav.navigate("form") }) { Text("Publier") } }
                    composable("form") { Button(onClick = { nav.navigate("more") }) { Text("Suite") } }
                    composable("more") { Text("Fin") }
                }
                TapGuardWhileNavigating(nav)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a second tap while the screen opens does not open it twice`() {
        setUp()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Publier").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("Publier").performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // One back, and the list shows again: a second form would still be there.
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        assertEquals("list", nav.currentDestination?.route)
    }

    @Test
    fun `once the screen is open its buttons answer again`() {
        setUp()
        compose.onNodeWithText("Publier").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Suite").performClick()
        compose.waitForIdle()
        assertEquals("more", nav.currentDestination?.route)
    }
}
