package fr.familia.app.feature.members.admin

import fr.familia.app.core.model.Feature
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeatureHelpTest {

    @Test
    fun `every module says what it is for under its switch`() {
        val missing = Feature.entries.filter { featureHelpRes(it) == null }
        assertTrue("Ajoute une phrase dans FeatureHelp.kt pour $missing", missing.isEmpty())
    }

    @Test
    fun `each sentence stays within 140 characters`() {
        val strings = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
        val long = Regex("""<string name="(feature_help_[a-z0-9_]+)">(.*?)</string>""")
            .findAll(strings.readText())
            .map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
            .filter { (_, text) -> text.length > 140 }
            .toList()
        assertTrue("Trop longues : $long", long.isEmpty())
    }
}
