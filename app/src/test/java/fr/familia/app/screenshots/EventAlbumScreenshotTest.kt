package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.album.AlbumPhotosSection
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.detail.EventDetailScreen
import fr.familia.app.feature.events.detail.previewEventDetail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The album of an event (récit 41) inside the event, in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class EventAlbumScreenshotTest {

    // Nothing being sent: an endless progress bar keeps the capture waiting.
    private val event: @Composable () -> Unit = {
        Surface {
            EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}) {
                AlbumPhotosSection(previewAlbumPhotos(folder = "events/1", count = 4), PhotoActions(), "")
            }
        }
    }

    @Test
    fun eventAlbumLight() = capture("evenement_album_clair", content = event)

    @Test
    @Config(qualifiers = "+night")
    fun eventAlbumDark() = capture("evenement_album_sombre", darkTheme = true, content = event)

    @Test
    @Config(fontScale = 2f)
    fun eventAlbumFontScale200() = capture("evenement_album_police_200", content = event)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
