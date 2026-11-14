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
import fr.familia.app.feature.events.edit.EventEditScreen
import fr.familia.app.feature.events.edit.EventFormError
import fr.familia.app.feature.events.edit.previewEventState
import fr.familia.app.feature.planning.PlanningScreen
import fr.familia.app.feature.planning.previewPlanning
import fr.familia.app.feature.members.edit.FormError
import fr.familia.app.feature.members.edit.MemberEditScreen
import fr.familia.app.feature.members.edit.previewEditState
import fr.familia.app.feature.members.edit.previewExistingState
import fr.familia.app.feature.members.edit.MemberEditUiState
import java.time.LocalDate
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
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    @Config(qualifiers = "+night")
    fun membersDark() = capture("famille_sombre", darkTheme = true) {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
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
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    fun membersErrorLight() = capture("famille_erreur_clair") {
        Surface { MembersScreen(MembersUiState.Error, onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    @Config(fontScale = 2f)
    fun offlineFontScale200() = capture("hors_ligne_police_200") {
        FamiliaApp(container = null, currentMember = sampleMember, isOffline = true)
    }

    @Test
    fun memberFormLight() = capture("fiche_membre_clair") {
        MemberEditScreen(previewEditState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    @Config(qualifiers = "+night")
    fun memberFormErrorsDark() = capture("fiche_membre_erreurs_sombre", darkTheme = true) {
        MemberEditScreen(
            previewEditState(errors = setOf(FormError.EmailTaken, FormError.ParentsMissing)),
            {}, {}, {}, {},
            today = LocalDate.of(2026, 11, 14),
        )
    }

    @Test
    @Config(fontScale = 2f)
    fun memberFormFontScale200() = capture("fiche_membre_police_200") {
        MemberEditScreen(previewEditState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    @Config(fontScale = 2f)
    fun memberFormExistingFontScale200() = capture("fiche_membre_existant_police_200") {
        MemberEditScreen(previewExistingState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    fun memberAddedLight() = capture("membre_ajoute_clair") {
        MemberEditScreen(MemberEditUiState.Added("Lou", "lou@example.com"), {}, {}, {}, {})
    }

    @Test
    fun planningListLight() = capture("planning_liste_clair") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningListDark() = capture("planning_liste_sombre", darkTheme = true) {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun planningListFontScale200() = capture("planning_liste_police_200") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    fun eventFormLight() = capture("evenement_clair") {
        EventEditScreen(previewEventState(isNew = false), {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun eventFormErrorsFontScale200() = capture("evenement_erreurs_police_200") {
        EventEditScreen(
            previewEventState(errors = setOf(EventFormError.TitleMissing, EventFormError.DateMissing)).let {
                it.copy(form = it.form.copy(title = "", date = null))
            },
            {}, {}, {}, {},
        )
    }

    @Test
    fun membersAdminLight() = capture("famille_admin_clair") {
        Surface {
            MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0", onEditMember = {})
        }
    }

    private val sampleMember = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, true, emptyList(), null)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
