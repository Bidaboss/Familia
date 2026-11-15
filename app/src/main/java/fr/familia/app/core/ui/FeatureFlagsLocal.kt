package fr.familia.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.core.model.FeatureFlags

/**
 * The module switches of config/app, provided by FamiliaApp. Previews and tests see every module
 * off unless they provide their own. Read it with `LocalFeatureFlags.current.isOn(Feature.Gifts)`.
 */
val LocalFeatureFlags = staticCompositionLocalOf { FeatureFlags() }
