package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.auth.SessionUiState
import fr.familia.app.feature.auth.SignInScreen
import fr.familia.app.feature.birthdays.BirthdaysScreen
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.feature.members.MembersScreen
import fr.familia.app.feature.members.MembersUiState
import fr.familia.app.feature.members.previewGroups
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens on the JVM and saves PNG files, so each pull request carries light and dark
 * screenshots without a phone. CI runs `recordRoborazziDebug` and uploads build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun planningLight() = capture("planning_clair") {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningDark() = capture("planning_sombre", darkTheme = true) {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    @Config(fontScale = 2f)
    fun planningFontScale200() = capture("planning_police_200") {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    fun birthdaysLight() = capture("anniversaires_clair") { Surface { BirthdaysScreen() } }

    @Test
    @Config(qualifiers = "+night")
    fun birthdaysDark() = capture("anniversaires_sombre", darkTheme = true) { Surface { BirthdaysScreen() } }

    @Test
    fun membersLight() = capture("famille_clair") {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), versionName = "0.1.0") }
    }

    @Test
    @Config(qualifiers = "+night")
    fun membersDark() = capture("famille_sombre", darkTheme = true) {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), versionName = "0.1.0") }
    }

    @Test
    fun signInLight() = capture("connexion_clair") { Surface { SignInScreen(SessionUiState.SignedOut(), {}, {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun signInDark() = capture("connexion_sombre", darkTheme = true) {
        Surface { SignInScreen(SessionUiState.SignedOut(), {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun signInNotAMemberFontScale200() = capture("connexion_refusee_police_200") {
        Surface { SignInScreen(SessionUiState.NotAMember("prenom.nom@gmail.com"), {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun membersFontScale200() = capture("famille_police_200") {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), versionName = "0.1.0") }
    }

    private val sampleMember = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, true, emptyList(), null)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
