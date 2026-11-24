package fr.familia.app.feature.trips.detail

import androidx.compose.ui.graphics.Color

/** The fixed colours of the illustration (maquette R6): a sky, dunes and a sand path. */
internal object TripPathColors {
    val Sky = Color(0xFFDDEFF7)
    val Ink = Color(0xFF1E1B18)
    val InkSoft = Color(0xFF2E4A57)
    val Sun = Color(0xFFFFD66B)
    val DuneFar = Color(0xFFF3D9A6)
    val DuneMid = Color(0xFFE8BE7A)
    val DuneNear = Color(0xFFC99A55)
    val Roof = Color(0xFFB5541C)
    val Road = Color(0xFFFFF6DA)
    val RoadDash = Color(0xFF8A5A3B)
    val Button = Color(0xFFFDF1EA)
}

/**
 * The white card of « Le programme » on the sky of the trip (récit 103): dark ink on white in
 * every theme, so these do not follow the Argile light/dark tokens on purpose. One place for
 * them instead of colours written in the screens.
 */
internal object TripSheetColors {
    val Card = Color.White
    val Ink = Color(0xFF111111)
    val InkSoft = Color(0xFF3A4A52)

    /** The dark blue of the rounds, the « Annuler » band and the « + » of the section. */
    val Deep = Color(0xFF17333F)
    val Track = Color(0xFFDCE3E8)
    val Panel = Color(0xFFF3F6F8)
    val Disabled = Color(0xFFE6EBEE)
    val OnDisabled = Color(0xFF4F5B63)

    /** The « Il manque… » band: pale yellow with brown ink. */
    val Warning = Color(0xFFFFE9A8)
    val OnWarning = Color(0xFF3D2E00)

    /** « Annuler » on the dark band and the « Voyage programmé » label of the cover. */
    val Highlight = Color(0xFFFFD84A)
}
