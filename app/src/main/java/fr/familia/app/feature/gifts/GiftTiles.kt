package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.feature.feed.FeedTone

/** The edge of the tiles of Cadeaux (refonte globale, lot 2): peach, dark on a light theme, light on a dark one. */
@Composable
fun giftAccent(): Color = if (LocalClayColors.current.tintAsStripe) FeedTone.Peach.bar else FeedTone.Peach.label
