package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.trips.detail.TripDetailScreen
import fr.familia.app.feature.trips.detail.previewTripDetail
import fr.familia.app.feature.trips.edit.TripEditScreen
import fr.familia.app.feature.trips.edit.previewTripEditState
import fr.familia.app.feature.trips.list.TripsScreen
import fr.familia.app.feature.trips.list.previewTrips
import fr.familia.app.feature.trips.step.StepEditScreen
import fr.familia.app.feature.trips.step.previewStepEditState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The trip screens (V3) in light, dark and at 200 % font size, like ScreenshotTest. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class TripScreenshotTest {

    private val list: @Composable () -> Unit = { Surface { TripsScreen(previewTrips(), {}, {}, {}, {}) } }
    private val detail: @Composable () -> Unit = { Surface { TripDetailScreen(previewTripDetail(), {}, {}, {}, {}, {}, {}, {}) } }
    private val follower: @Composable () -> Unit = {
        Surface { TripDetailScreen(previewTripDetail(isParticipant = false), {}, {}, {}, {}, {}, {}, {}) }
    }
    // Without steps, so the checklist (récit 13) shows on screen.
    private val checklist: @Composable () -> Unit = {
        Surface { TripDetailScreen(previewTripDetail().copy(steps = emptyList()), {}, {}, {}, {}, {}, {}, {}) }
    }
    // Grey boxes stand for the photos: Storage is not reached in a test.
    private val photos: @Composable () -> Unit = { Surface { AlbumScreen("Photos", previewAlbumPhotos(), PhotoActions(), {}) } }
    private val edit: @Composable () -> Unit = { Surface { TripEditScreen(previewTripEditState(isNew = false), {}, {}, {}, {}) } }
    private val step: @Composable () -> Unit = { Surface { StepEditScreen(previewStepEditState(), {}, {}, {}, {}) } }

    @Test
    fun tripsLight() = capture("voyages_clair", content = list)

    @Test
    @Config(qualifiers = "+night")
    fun tripsDark() = capture("voyages_sombre", darkTheme = true, content = list)

    @Test
    @Config(fontScale = 2f)
    fun tripsFontScale200() = capture("voyages_police_200", content = list)

    @Test
    fun tripLight() = capture("voyage_clair", content = detail)

    @Test
    @Config(qualifiers = "+night")
    fun tripDark() = capture("voyage_sombre", darkTheme = true, content = detail)

    @Test
    @Config(fontScale = 2f)
    fun tripFontScale200() = capture("voyage_police_200", content = detail)

    @Test
    fun tripFollowerLight() = capture("voyage_lecteur_clair", content = follower)

    @Test
    fun checklistLight() = capture("voyage_liste_clair", content = checklist)

    @Test
    @Config(qualifiers = "+night")
    fun checklistDark() = capture("voyage_liste_sombre", darkTheme = true, content = checklist)

    @Test
    @Config(fontScale = 2f)
    fun checklistFontScale200() = capture("voyage_liste_police_200", content = checklist)

    @Test
    fun photosLight() = capture("voyage_photos_clair", content = photos)

    @Test
    @Config(qualifiers = "+night")
    fun photosDark() = capture("voyage_photos_sombre", darkTheme = true, content = photos)

    @Test
    @Config(fontScale = 2f)
    fun photosFontScale200() = capture("voyage_photos_police_200", content = photos)

    @Test
    fun tripEditLight() = capture("voyage_modifier_clair", content = edit)

    @Test
    @Config(qualifiers = "+night")
    fun tripEditDark() = capture("voyage_modifier_sombre", darkTheme = true, content = edit)

    @Test
    @Config(fontScale = 2f)
    fun tripEditFontScale200() = capture("voyage_modifier_police_200", content = edit)

    @Test
    fun stepEditLight() = capture("etape_clair", content = step)

    @Test
    @Config(qualifiers = "+night")
    fun stepEditDark() = capture("etape_sombre", darkTheme = true, content = step)

    @Test
    @Config(fontScale = 2f)
    fun stepEditFontScale200() = capture("etape_police_200", content = step)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
