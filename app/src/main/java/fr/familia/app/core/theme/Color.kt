package fr.familia.app.core.theme

import androidx.compose.ui.graphics.Color

// Fixed Familia palette built around the terracotta seed #B5541C (design principles, "Thème Material 3").
// Dynamic color is deliberately off so every phone shows the same colors and phone explanations
// ("touche le bouton orange") stay true.
// Values follow the Material 3 tonal roles; the DESIGN thread may replace them with an exact
// Material Theme Builder export without touching any screen.

// Light
internal val PrimaryLight = Color(0xFFB5541C)
internal val OnPrimaryLight = Color(0xFFFFFFFF)
internal val PrimaryContainerLight = Color(0xFFFFDBCB)
internal val OnPrimaryContainerLight = Color(0xFF351000)
internal val SecondaryLight = Color(0xFF77574A)
internal val OnSecondaryLight = Color(0xFFFFFFFF)
internal val SecondaryContainerLight = Color(0xFFFFDBCF)
internal val OnSecondaryContainerLight = Color(0xFF2C160C)
internal val TertiaryLight = Color(0xFF665F31)
internal val OnTertiaryLight = Color(0xFFFFFFFF)
internal val TertiaryContainerLight = Color(0xFFEEE3A9)
internal val OnTertiaryContainerLight = Color(0xFF1F1C00)
internal val ErrorLight = Color(0xFFBA1A1A)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorContainerLight = Color(0xFFFFDAD6)
internal val OnErrorContainerLight = Color(0xFF410002)
// Style Argile (design principles, carte DESIGN-3): warm cream background.
internal val SurfaceLight = Color(0xFFFFF6F0)
internal val OnSurfaceLight = Color(0xFF231A16)
internal val SurfaceVariantLight = Color(0xFFF5DED5)
internal val OnSurfaceVariantLight = Color(0xFF53443D)
internal val OutlineLight = Color(0xFF85736C)
internal val OutlineVariantLight = Color(0xFFD8C2BA)

// Dark
internal val PrimaryDark = Color(0xFFFFB693)
internal val OnPrimaryDark = Color(0xFF552000)
internal val PrimaryContainerDark = Color(0xFF7A3209)
internal val OnPrimaryContainerDark = Color(0xFFFFDBCB)
internal val SecondaryDark = Color(0xFFE7BEAE)
internal val OnSecondaryDark = Color(0xFF442A1F)
internal val SecondaryContainerDark = Color(0xFF5D4034)
internal val OnSecondaryContainerDark = Color(0xFFFFDBCF)
internal val TertiaryDark = Color(0xFFD1C78F)
internal val OnTertiaryDark = Color(0xFF363107)
internal val TertiaryContainerDark = Color(0xFF4D471C)
internal val OnTertiaryContainerDark = Color(0xFFEEE3A9)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val OnErrorDark = Color(0xFF690005)
internal val ErrorContainerDark = Color(0xFF93000A)
internal val OnErrorContainerDark = Color(0xFFFFDAD6)
internal val SurfaceDark = Color(0xFF1E1511)
internal val OnSurfaceDark = Color(0xFFEBDCD3)
internal val SurfaceVariantDark = Color(0xFF53443D)
internal val OnSurfaceVariantDark = Color(0xFFD8C2BA)
internal val OutlineDark = Color(0xFFA08D85)
internal val OutlineVariantDark = Color(0xFF53443D)
