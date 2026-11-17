package fr.familia.app.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Error and refusal texts are read by every member, grandparents included: plain words, never a
 * technical term. The admin-only screens (crash reports, consumption, Firebase switches) may name
 * their tools, since only the main admin sees them.
 */
class ErrorTextsTest {

    private val adminOnly = Regex("^(crashes_|consumption_|administration_feature_|channel_admin_)")
    private val errorKey = Regex("(_error|_failed|_refused|_unreadable|_unavailable|_missing)")
    private val forbidden = Regex("""(?i)\b(erreur|error|exception|permission denied|firebase|firestore|serveur|server|null|code \d+)\b""")

    private fun texts(): List<Pair<String, String>> {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
        return Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""")
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
            .filter { (name, _) -> errorKey.containsMatchIn(name) && !adminOnly.containsMatchIn(name) && !name.startsWith("help_") }
            .toList()
    }

    @Test
    fun `no error text shows a technical word`() {
        val bad = texts().filter { (_, text) -> forbidden.containsMatchIn(text) }
        assertTrue("Mots techniques à l'écran : $bad", bad.isEmpty())
    }

    @Test
    fun `a failed action always says what to do next`() {
        val next = Regex("(?i)(réessa|vérifie|choisis|regarde|touche|installe|télécharge|essaie|enlève|retire|rapproche|ajoute|dis-le|signale)")
        val bad = texts().filter { (name, text) -> name.endsWith("_failed") && !next.containsMatchIn(text) }
        assertTrue("Dis quoi faire après : $bad", bad.isEmpty())
    }
}
