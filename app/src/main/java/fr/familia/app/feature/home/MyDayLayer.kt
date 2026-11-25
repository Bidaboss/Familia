package fr.familia.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.surprisesToConfirm
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.LocalPhotoHaze
import fr.familia.app.core.theme.PhotoBackdrop
import fr.familia.app.core.theme.PhotoScroll
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.PhotoTitleShadow
import fr.familia.app.core.theme.themeBackground

/*
 * La gazette est l'Accueil (récit 115, brief §3) : « Ma journée » n'est plus un onglet mais un écran
 * à part, posé AU-DESSUS de la gazette dans l'Accueil (pas une page de navigation). La fermeture éclair
 * (MyDayZipper.kt) le dévoile derrière la gazette ; la ligne de résumé de l'en-tête l'ouvre aussi, et
 * elle reste l'accès sans geste (TalkBack, animations réduites).
 */

/** What « Ma journée » counts for the line of the header: the things of today, and what waits for me. */
data class MyDaySummary(val today: Int, val toAnswer: Int)

/**
 * The line of the header: the things on today's frise (events, birthdays, coups de main) and what
 * waits for an answer in « Ma journée » (answers, surprises to confirm or rate, coups de main).
 */
fun myDaySummary(
    ready: HomeUiState.Ready,
    days: List<HomeDay>,
    surprises: List<Surprise>,
    toRate: Int,
    helpTodos: Int,
    surprisesOn: Boolean,
): MyDaySummary {
    val today = days.firstOrNull { it.date == ready.today }?.items?.size ?: 0
    val surprise = if (surprisesOn) surprisesToConfirm(surprises, ready.today)?.count ?: 0 else 0
    val rate = if (surprisesOn) toRate else 0
    return MyDaySummary(today, ready.todos.size + surprise + rate + helpTodos)
}

/**
 * « ☀️ 2 événements · 3 à répondre › » (or « Rien de prévu »), under « Bonjour » (fiche S1, Y1): a
 * button that opens « Ma journée », 56 dp high. On the photo it is white with a shadow.
 */
@Composable
fun MyDaySummaryLine(summary: MyDaySummary, onPhoto: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val parts = buildList {
        add(
            if (summary.today == 0) {
                stringResource(R.string.home_myday_nothing)
            } else {
                pluralStringResource(R.plurals.home_myday_events, summary.today, summary.today)
            },
        )
        if (summary.toAnswer > 0) add(pluralStringResource(R.plurals.home_myday_to_answer, summary.toAnswer, summary.toAnswer))
    }
    val text = parts.joinToString(" · ")
    val spoken = stringResource(R.string.home_myday_open_a11y, text)
    val color = if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        shadow = if (onPhoto) PhotoTitleShadow else null,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
    ) {
        Text("☀️ ", style = style)
        Text("$text ›", style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** « Ma journée » and its round ✕ (fiche W1), at the top of the screen of the day. */
@Composable
internal fun MyDayTitle(onPhoto: Boolean, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            stringResource(R.string.home_photo_my_day),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, shadow = if (onPhoto) PhotoSectionShadow else null),
            color = if (onPhoto) PhotoSectionText else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MyDayCloseColor), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_myday_close), tint = PhotoBrown)
            }
        }
    }
}

/** The room under the content of « Ma journée », so it never goes under the zipper at the bottom (brief §7). */
internal val MY_DAY_ZIPPER_ROOM = 140.dp

/** The room under the list of « Ma journée » on the photo: 24 dp, or [MY_DAY_ZIPPER_ROOM] behind the zipper. */
internal val LocalMyDayBottom = androidx.compose.runtime.staticCompositionLocalOf { 24.dp }

/** The round of the ✕ (W1, then the « Carnet post-it » of the brief §8). */
private val MyDayCloseColor = Color(0xFFF0E3C4)

/**
 * The screen of « Ma journée », over the gazette: on its own copy of the photo of the theme (with its
 * own blur) or on the background of the theme. It takes every touch, so the gazette under it never
 * reacts.
 */
@Composable
internal fun MyDayLayer(photoTheme: FamiliaThemeId?, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (photoTheme == null) Modifier.themeBackground() else Modifier)
            // Any touch on the layer stays on it: the gazette under it is not hit.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        if (photoTheme != null) {
            val scroll = remember { PhotoScroll() }
            CompositionLocalProvider(LocalPhotoHaze provides remember { dev.chrisbanes.haze.HazeState() }) {
                PhotoBackdrop(photoTheme, scroll)
                Box(Modifier.fillMaxSize().nestedScroll(scroll.connection)) { content() }
            }
        } else {
            content()
        }
    }
}
