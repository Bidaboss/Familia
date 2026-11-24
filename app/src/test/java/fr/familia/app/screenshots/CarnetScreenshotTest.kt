package fr.familia.app.screenshots

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.model.TravelPlace
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.trips.carnet.CarnetScreen
import fr.familia.app.feature.trips.carnet.CarnetStop
import fr.familia.app.feature.trips.carnet.CarnetTrip
import fr.familia.app.feature.trips.carnet.CarnetUiState
import fr.familia.app.feature.trips.carnet.carnetOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** « Carnet de baroudeur » (récit 107) in light, dark and at 200 % font size. Fictional trips. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class CarnetScreenshotTest {
    private val manual = TravelPlace("p1", "Lisbonne", "Portugal", "PT", 38.72, -9.14, LocalDate.of(2019, 5, 12), 5)

    private val state = CarnetUiState.Ready(
        carnet = carnetOf(
            listOf(
                CarnetTrip(LocalDate.of(2026, 4, 10), 5, listOf(CarnetStop("Marrakech", 31.63, -8.0, "MA")), photos = 64),
                CarnetTrip(LocalDate.of(2025, 8, 2), 8, listOf(CarnetStop("Lyon", 45.76, 4.84, "FR"), CarnetStop("Rome", 41.9, 12.5, "IT")), photos = 112),
                CarnetTrip(manual.date, manual.days, listOf(CarnetStop(manual.name, manual.lat, manual.lng, "PT")), manual = true),
            ),
            45.76 to 4.84,
        ),
        manual = listOf(manual),
        today = LocalDate.of(2026, 11, 24),
        canAdd = true,
    )

    private val screen: @Composable () -> Unit = { CarnetScreen(state, onBack = {}, onAdd = {}, onRemove = {}) }

    @Test
    fun carnetLight() = capture("carnet_clair", content = screen)

    @Test
    @Config(qualifiers = "+night")
    fun carnetDark() = capture("carnet_sombre", darkTheme = true, content = screen)

    @Test
    @Config(fontScale = 2f)
    fun carnetFontScale200() = capture("carnet_police_200", content = screen)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
