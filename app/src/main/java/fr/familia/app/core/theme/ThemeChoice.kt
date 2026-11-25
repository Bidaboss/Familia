package fr.familia.app.core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate

/**
 * What a phone chose in Réglages > Thème (récit 113, règles 14 à 16): « Selon la saison », the
 * default, or one photo theme. Kept on the phone only, by [key].
 */
sealed interface ThemeChoice {
    val key: String

    /** The photo theme shown on [date]. */
    fun on(date: LocalDate): FamiliaThemeId

    /** « Selon la saison » (DESIGN-156 A): the photo and its colors change with the season. */
    data object Season : ThemeChoice {
        override val key = "season"
        override fun on(date: LocalDate) = seasonTheme(date)
    }

    data class Photo(val theme: FamiliaThemeId) : ThemeChoice {
        init {
            require(theme.isPhoto) { "$theme has no photo" }
        }

        override val key get() = theme.key
        override fun on(date: LocalDate) = theme
    }

    companion object {
        /** The choices of Réglages > Thème, « Selon la saison » first. */
        val all: List<ThemeChoice> = listOf(Season) + FamiliaThemeId.photos.map(::Photo)

        /**
         * The choice kept under [key]: a photo theme, else « Selon la saison ». The 7 color themes of
         * before (classic, garden, spain, winter, lavender, night, contrast, and the older ones) all
         * go to « Selon la saison » (A5, Migration); « contrast » keeps « Contraste fort » apart.
         */
        fun fromKey(key: String?): ThemeChoice = FamiliaThemeId.photos.firstOrNull { it.key == key }?.let(::Photo) ?: Season
    }
}

/**
 * The photo theme of the season on [date] (A5): winter, 21/12 to 19/03, Mer de nuages; spring,
 * 20/03 to 20/06, Lac de montagne; summer, 21/06 to 22/09, Côte sauvage; autumn, 23/09 to 20/12,
 * Automne.
 */
fun seasonTheme(date: LocalDate): FamiliaThemeId {
    val day = date.monthValue * 100 + date.dayOfMonth
    return when {
        day >= 1221 || day < 320 -> FamiliaThemeId.Clouds
        day < 621 -> FamiliaThemeId.Lake
        day < 923 -> FamiliaThemeId.Coast
        else -> FamiliaThemeId.Autumn
    }
}

/** The photo theme of the screen, for the home and the banners (règles 9 et 20); null without photo. */
val LocalPhotoTheme = staticCompositionLocalOf<FamiliaThemeId?> { null }
