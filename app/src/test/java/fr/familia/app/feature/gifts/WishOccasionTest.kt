package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.defaultWishOccasion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The occasion of a reservation of « Mes envies » without a choice (PRD rév. 186), as the server reckons it. */
class WishOccasionTest {

    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun `the birthday or Christmas, whichever comes first`() {
        assertEquals(GiftOccasion.Christmas, defaultWishOccasion(day("1990-03-14"), day("2026-11-16")))
        assertEquals(GiftOccasion.Birthday, defaultWishOccasion(day("1990-12-01"), day("2026-11-16")))
        assertEquals(GiftOccasion.Birthday, defaultWishOccasion(day("1990-03-14"), day("2026-12-26")))
        assertEquals(GiftOccasion.Christmas, defaultWishOccasion(null, day("2026-12-26")))
        // The day itself still counts, and 29 February is the 28th in another year.
        assertEquals(GiftOccasion.Birthday, defaultWishOccasion(day("1990-03-14"), day("2027-03-14")))
        assertEquals(GiftOccasion.Birthday, defaultWishOccasion(day("2000-02-29"), day("2027-01-10")))
    }
}
