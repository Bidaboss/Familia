package fr.familia.app.feature.a11y

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The contrast pass on the screens of the voyage thread ([VoyageScreens]). Fictional family only.
 * The photo viewer opens in a window of its own, that the pass does not see: white on black, by hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class VoyageScreensContrastTest : ThemeLooksContrastTest(VoyageScreens.all.filter { it.first != "the viewer of a photo" })
