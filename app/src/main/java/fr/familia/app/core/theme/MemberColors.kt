package fr.familia.app.core.theme

import androidx.compose.ui.graphics.Color

/**
 * One color per member (design principles, "Couleur par membre"). All are dark enough for a white
 * initial (contrast of at least 4.5:1). A color is never the only clue: the initial and the first
 * name are always shown with it.
 */
val MemberColors: List<Color> = listOf(
    Color(0xFF9C4146),
    Color(0xFF8B5000),
    Color(0xFF6D5E0F),
    Color(0xFF3B6939),
    Color(0xFF006A60),
    Color(0xFF006590),
    Color(0xFF4355B9),
    Color(0xFF6750A4),
    Color(0xFF8E4585),
    Color(0xFF7D5260),
    Color(0xFF5D5F5F),
    Color(0xFF765A00),
    Color(0xFF4A6267),
)

val OnMemberColor: Color = Color.White
