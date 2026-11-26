package fr.familia.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R

/*
 * « Carnet post-it » (brief §8, choisi par l'admin) : dans Ma journée, les cartes deviennent des notes
 * collées sur une page de carnet. [LocalClayNote] est donné par Ma journée à chaque bloc ; une
 * ClayCard qui le trouve se dessine en note (couleur, légère inclinaison, ombre douce, coins de 4 dp,
 * sans bordure). Partout ailleurs, rien ne change.
 */

/** A note of the carnet: its paper and its small tilt. */
data class ClayNote(val color: Color, val degrees: Float) {
    companion object {
        /** The first note, yellow, tilted −0,7°. */
        val Yellow = ClayNote(Color(0xFFFFF3B8), -0.7f)

        /** The second note, pink, tilted +0,6°. */
        val Pink = ClayNote(Color(0xFFFFD9D0), 0.6f)
    }
}

/** The note the cards of this block are drawn as, or null for the usual card. */
val LocalClayNote = staticCompositionLocalOf<ClayNote?> { null }

/** The handwritten titles of the carnet are on: [SectionLabel] writes in Caveat. */
val LocalCarnetTitles = staticCompositionLocalOf { false }

/** Caveat (SIL Open Font License, assets/licences), embedded so every phone writes the same. */
@OptIn(ExperimentalTextApi::class)
val Caveat = FontFamily(Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))

/** The ink of the carnet. */
val CarnetInk = Color(0xFF7A4B1E)

/** « Ma journée » and the titles of its sections: Caveat 700, 28 sp, brown. */
val CarnetTitle = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, color = CarnetInk)

private val NoteShape = RoundedCornerShape(4.dp)

/** A card drawn as [note]: the same content, on a tilted note with a soft shadow. */
@Composable
internal fun NoteCard(
    note: ClayNote,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .rotate(note.degrees)
            .shadow(3.dp, NoteShape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .background(note.color, NoteShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
    ) {
        // The carnet is a light page: dark ink on the note, whatever the theme of the phone.
        CompositionLocalProvider(LocalContentColor provides Color(0xFF3B2A1E)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
