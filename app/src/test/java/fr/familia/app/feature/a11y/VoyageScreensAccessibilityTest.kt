package fr.familia.app.feature.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.ui.checkAccessibility
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Police 200 % and TalkBack on the screens of the voyage thread (voyages et À emporter, albums,
 * visionneuse, diaporama, recettes et livre, capsule, arbre et affiche, Qui est-ce ?), with their
 * preview states and the common check of [checkAccessibility]. Fictional family only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class VoyageScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the trips`() = compose.checkAccessibility(VoyageScreens.screen("the trips"))

    @Test
    fun `a new trip`() = compose.checkAccessibility(VoyageScreens.screen("a new trip"))

    @Test
    fun `edit a trip, with errors`() = compose.checkAccessibility(VoyageScreens.screen("edit a trip, with errors"))

    @Test
    fun `a new step`() = compose.checkAccessibility(VoyageScreens.screen("a new step"))

    @Test
    fun `edit a step, with errors`() = compose.checkAccessibility(VoyageScreens.screen("edit a step, with errors"))

    @Test
    fun `a trip`() = compose.checkAccessibility(VoyageScreens.screen("a trip"))

    @Test
    fun `a trip, the path of the fiche F1`() = compose.checkAccessibility(VoyageScreens.screen("a trip, the path of the fiche F1"))

    @Test
    fun `a trip, for who does not take part`() = compose.checkAccessibility(VoyageScreens.screen("a trip, for who does not take part"))

    @Test
    fun `the dates of a trip`() = compose.checkAccessibility(VoyageScreens.screen("the dates of a trip"))

    @Test
    fun `where are you going`() = compose.checkAccessibility(VoyageScreens.screen("where are you going"))

    @Test
    fun `the page of a destination`() = compose.checkAccessibility(VoyageScreens.screen("the page of a destination"))

    @Test
    fun `enrich with an AI`() = compose.checkAccessibility(VoyageScreens.screen("enrich with an AI"))

    @Test
    fun `enrich, an answer refused`() = compose.checkAccessibility(VoyageScreens.screen("enrich, an answer refused"))

    @Test
    fun `enrich, the check`() = compose.checkAccessibility(VoyageScreens.screen("enrich, the check"))

    @Test
    fun `the currency of a trip`() = compose.checkAccessibility(VoyageScreens.screen("the currency of a trip"))

    @Test
    fun `the currency of a trip, for who does not manage it`() = compose.checkAccessibility(VoyageScreens.screen("the currency of a trip, for who does not manage it"))
    fun `the documents of a trip`() = compose.checkAccessibility(VoyageScreens.screen("the documents of a trip"))

    @Test
    fun `À emporter`() = compose.checkAccessibility(VoyageScreens.screen("À emporter"))

    @Test
    fun `the albums`() = compose.checkAccessibility(VoyageScreens.screen("the albums"))

    @Test
    fun `an album`() = compose.checkAccessibility(VoyageScreens.screen("an album"))

    @Test
    fun `the viewer of a photo`() = compose.checkAccessibility(VoyageScreens.screen("the viewer of a photo"))

    @Test
    fun `the slideshow of the month`() = compose.checkAccessibility(VoyageScreens.screen("the slideshow of the month"))

    @Test
    fun `the recipes`() = compose.checkAccessibility(VoyageScreens.screen("the recipes"))

    @Test
    fun `a recipe`() = compose.checkAccessibility(VoyageScreens.screen("a recipe"))

    @Test
    fun `the book of recipes`() = compose.checkAccessibility(VoyageScreens.screen("the book of recipes"))

    @Test
    fun `the capsules`() = compose.checkAccessibility(VoyageScreens.screen("the capsules"))

    @Test
    fun `a capsule`() = compose.checkAccessibility(VoyageScreens.screen("a capsule"))

    @Test
    fun `write a capsule`() = compose.checkAccessibility(VoyageScreens.screen("write a capsule"))

    @Test
    fun `the tree`() = compose.checkAccessibility(VoyageScreens.screen("the tree"))

    @Test
    fun `the tree of life`() = compose.checkAccessibility(VoyageScreens.screen("the tree of life"))

    @Test
    fun `the tree of life, without relatives`() = compose.checkAccessibility(VoyageScreens.screen("the tree of life, without relatives"))

    @Test
    fun `the tree of life, four generations`() = compose.checkAccessibility(VoyageScreens.screen("the tree of life, four generations"))

    @Test
    fun `the fil, photos and a recipe`() = compose.checkAccessibility(VoyageScreens.screen("the fil, photos and a recipe"))


    @Test
    fun `Qui est-ce ?, the list`() = compose.checkAccessibility(VoyageScreens.screen("Qui est-ce ?, the list"))

    @Test
    fun `Qui est-ce ?, a photo to guess`() = compose.checkAccessibility(VoyageScreens.screen("Qui est-ce ?, a photo to guess"))

    @Test
    fun `Qui est-ce ?, the answer`() = compose.checkAccessibility(VoyageScreens.screen("Qui est-ce ?, the answer"))
}
