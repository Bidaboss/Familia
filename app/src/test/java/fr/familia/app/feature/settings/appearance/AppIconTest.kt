package fr.familia.app.feature.settings.appearance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {

    @Test
    fun theChosenAliasIsEnabledBeforeTheOthersAreDisabled() {
        val changes = AppIcon.changesFor(AppIcon.Terre)
        assertEquals(AppIcon.Terre to true, changes.first())
        assertEquals(setOf(AppIcon.Olivier to false, AppIcon.Or to false), changes.drop(1).toSet())
    }

    @Test
    fun oliveTreeIsShownByDefaultAndWhenTheStatesAreMixed() {
        assertEquals(AppIcon.Olivier, AppIcon.Default)
        assertEquals(AppIcon.Or, AppIcon.shown { it == AppIcon.Or })
        assertEquals(AppIcon.Olivier, AppIcon.shown { false })
        assertEquals(AppIcon.Olivier, AppIcon.shown { it != AppIcon.Olivier })
    }

    /** Each icon needs its alias in the manifest, and only the default one starts enabled. */
    @Test
    fun everyIconHasItsAliasInTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        AppIcon.entries.forEach { icon ->
            val alias = Regex("""<activity-alias[^>]*android:name="\.${icon.alias}"[^>]*>""").find(manifest)
            assertTrue("alias .${icon.alias} missing", alias != null)
            assertTrue(alias!!.value.contains("""android:enabled="${icon == AppIcon.Default}""""))
        }
        // MainActivity opens shared links (récit 31), but the launcher entries stay on the aliases.
        val mainActivity = Regex("""<activity\s[^>]*android:name="\.MainActivity"(?:[^>]*/>|[\s\S]*?</activity>)""").find(manifest)
        assertTrue("MainActivity missing", mainActivity != null)
        assertTrue(
            "MainActivity must not have its own launcher entry",
            !mainActivity!!.value.contains("android.intent.category.LAUNCHER"),
        )
    }
}
