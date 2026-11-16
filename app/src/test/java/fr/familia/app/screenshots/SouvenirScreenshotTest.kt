package fr.familia.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.SouvenirCounters
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.members.profile.MemberProfileScreen
import fr.familia.app.feature.members.profile.MemberProfileUiState
import fr.familia.app.feature.settings.NotificationSettingsScreen
import fr.familia.app.feature.settings.NotificationSettingsUiState
import fr.familia.app.feature.souvenirs.BravoContent
import fr.familia.app.feature.souvenirs.SouvenirKind
import fr.familia.app.feature.souvenirs.SouvenirMedal
import fr.familia.app.feature.souvenirs.previewSouvenirs
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The souvenirs (récit 34) in light, dark and at 200 % font size, like ScreenshotTest. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class SouvenirScreenshotTest {

    private val camille = Member("a", "Camille", null, LocalDate.of(1990, 5, 4), "h1", MemberProfile.Adult, true, emptyList(), null, colorIndex = 2)

    // My own page: souvenirs earned (one shown, one private) and the next one of each family, dotted.
    private val mine: @Composable () -> Unit = {
        Surface {
            MemberProfileScreen(
                MemberProfileUiState.Ready(
                    camille, "Foyer Martin", isMe = true, canEdit = true, souvenirs = previewSouvenirs(),
                    counters = SouvenirCounters(reactions = 4, helpGiven = 2, weeklyAnswers = 1),
                ),
                {}, {}, { _, _ -> },
            )
        }
    }

    // Someone else's page: only what they show, no switch, no Modifier button.
    private val other: @Composable () -> Unit = {
        Surface {
            MemberProfileScreen(
                MemberProfileUiState.Ready(
                    camille, "Foyer Martin", isMe = false, canEdit = false, souvenirs = previewSouvenirs().filter { it.shown },
                ),
                {}, {}, { _, _ -> },
            )
        }
    }

    private val medals: @Composable () -> Unit = {
        Surface {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SouvenirMedal(SouvenirKind.FirstGoodNews, Instant.parse("2026-12-02T10:00:00Z"))
                SouvenirMedal(SouvenirKind.HelpGiven5, null, counters = SouvenirCounters(helpGiven = 2))
                SouvenirMedal(SouvenirKind.OneYear, null)
            }
        }
    }

    // The « Bravo » sheet, drawn without its modal window (a popup is not captured).
    private val bravo: @Composable () -> Unit = {
        Surface { BravoContent(SouvenirKind.HelpGiven5, previewSouvenirs().last().copy(key = "helpGiven5"), {}, {}) }
    }

    // The Notifications settings with the module switched on, so the « Souvenirs » switch shows.
    private val settings: @Composable () -> Unit = {
        CompositionLocalProvider(LocalFeatureFlags provides FeatureFlags(mapOf(Feature.Souvenirs.key to true))) {
            Surface {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState.Ready(dayBeforeReminder = true, souvenirs = false),
                    notificationsAllowed = true,
                    onAllowNotifications = {},
                    onDayBeforeReminderChange = {},
                    onOpenSystemSettings = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun myProfileLight() = capture("fiche_moi_clair", content = mine)

    @Test
    @Config(qualifiers = "+night")
    fun myProfileDark() = capture("fiche_moi_sombre", darkTheme = true, content = mine)

    @Test
    @Config(fontScale = 2f)
    fun myProfileFontScale200() = capture("fiche_moi_police_200", content = mine)

    @Test
    fun otherProfileLight() = capture("fiche_autre_clair", content = other)

    @Test
    @Config(qualifiers = "+night")
    fun otherProfileDark() = capture("fiche_autre_sombre", darkTheme = true, content = other)

    @Test
    fun medalsLight() = capture("souvenirs_medaillons_clair", content = medals)

    @Test
    @Config(qualifiers = "+night")
    fun medalsDark() = capture("souvenirs_medaillons_sombre", darkTheme = true, content = medals)

    @Test
    fun bravoLight() = capture("souvenir_bravo_clair", content = bravo)

    @Test
    @Config(qualifiers = "+night")
    fun bravoDark() = capture("souvenir_bravo_sombre", darkTheme = true, content = bravo)

    @Test
    @Config(fontScale = 2f)
    fun bravoFontScale200() = capture("souvenir_bravo_police_200", content = bravo)

    @Test
    fun settingsLight() = capture("notifications_souvenirs_clair", content = settings)

    @Test
    @Config(qualifiers = "+night")
    fun settingsDark() = capture("notifications_souvenirs_sombre", darkTheme = true, content = settings)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
