package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.feature.feed.FeedTone

/** The edge of the tiles of Cadeaux (refonte globale, lot 2): peach, dark on a light theme, light on a dark one. */
@Composable
fun giftAccent(): Color = if (LocalClayColors.current.tintAsStripe) FeedTone.Peach.bar else FeedTone.Peach.label

/**
 * The dark of Cadeaux et Surprise (buttons, tab on, fiches C1 to C7): the dark of the photo theme
 * chosen, the brown of the fiches otherwise. It follows the theme (fiches, § 0).
 */
@Composable
fun surpriseDark(): Color = LocalPhotoTheme.current?.dark?.let { Color(it) } ?: Color(0xFF3A2A1A)

/** The accent of Cadeaux et Surprise (count pills, progress bar): the theme's, orange otherwise. */
@Composable
fun surpriseAccent(): Color = LocalPhotoTheme.current?.accent?.let { Color(it) } ?: Color(0xFFE08A3C)
