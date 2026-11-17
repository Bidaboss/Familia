package fr.familia.app.feature.a11y

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The contrast pass on the screens of the demo of the 21 ([DemoScreens]). Fictional family only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class DemoScreensContrastTest : ThemeLooksContrastTest(DemoScreens.all)
