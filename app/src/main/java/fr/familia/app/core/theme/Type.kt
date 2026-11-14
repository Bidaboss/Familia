package fr.familia.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

private fun TextStyle.sized(size: Int, lineHeight: Int, weight: FontWeight? = null) =
    copy(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight ?: fontWeight)

/**
 * Material 3 type scale raised one step for the grandparents (design principles, "Typographie").
 * Sizes are in sp so they follow the phone's font size setting. No style goes below 14 sp.
 */
internal val FamiliaTypography = Base.copy(
    headlineMedium = Base.headlineMedium.sized(28, 36),
    titleLarge = Base.titleLarge.sized(22, 28),
    titleMedium = Base.titleMedium.sized(18, 24),
    titleSmall = Base.titleSmall.sized(16, 22),
    bodyLarge = Base.bodyLarge.sized(18, 26),
    bodyMedium = Base.bodyMedium.sized(16, 24),
    bodySmall = Base.bodySmall.sized(14, 20),
    labelLarge = Base.labelLarge.sized(16, 20, FontWeight.Medium),
    labelMedium = Base.labelMedium.sized(14, 20, FontWeight.Medium),
    labelSmall = Base.labelSmall.sized(14, 18, FontWeight.Medium),
)
