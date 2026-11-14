package fr.familia.app.core.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Every screen is previewed in light, dark and at 200 % font size (design checklist). */
@Preview(name = "Clair", widthDp = 360)
@Preview(name = "Sombre", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Police 200 %", widthDp = 360, fontScale = 2f)
annotation class FamiliaPreviews
