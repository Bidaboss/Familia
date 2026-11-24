package fr.familia.app.feature.trips.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.TravelPlace
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The destinations added by hand to the carnet (récit 107, règle 38) show in « Souvenirs ». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xxhdpi")
class TripsHandAddedTest {

    @get:Rule
    val compose = createComposeRule()

    private val place = TravelPlace(
        id = "p1", name = "Lisbonne", country = "Portugal", countryCode = "PT",
        lat = 38.72, lng = -9.14, date = LocalDate.now().minusDays(1), days = 4,
    )

    @Test
    fun `a destination added by hand is a card of Souvenirs that opens the carnet`() {
        var opened = 0
        compose.setContent {
            FamiliaTheme {
                TripsScreen(previewTrips(), {}, {}, {}, {}, onOpenCarnet = { opened++ }, handAdded = listOf(place))
            }
        }
        compose.onNodeWithText("Souvenirs", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lisbonne, ", substring = true).performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("Ajouté à la main", substring = true).assertExists()
    }

    @Test
    fun `a child with no trip but a destination added by hand still finds it in Souvenirs`() {
        val empty = TripsUiState.Success(emptyList(), emptyList(), emptyList(), LocalDate.now(), canCreate = false)
        compose.setContent {
            FamiliaTheme { TripsScreen(empty, {}, {}, {}, {}, onOpenCarnet = {}, handAdded = listOf(place)) }
        }
        compose.onNodeWithText("Souvenirs", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lisbonne, ", substring = true).assertExists()
    }
}
