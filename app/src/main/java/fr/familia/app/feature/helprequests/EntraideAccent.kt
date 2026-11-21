package fr.familia.app.feature.helprequests

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.feature.feed.FeedTone

/** The lilac edge of the tiles of Entraide (coups de main, bons plans, on se prête), as in their forms. */
@Composable
fun entraideAccent(): Color = if (LocalClayColors.current.tintAsStripe) FeedTone.Lilac.bar else FeedTone.Lilac.label
