package fr.familia.app.core.ui.form

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

/**
 * After a refused « Enregistrer », the page goes up to the first tile to fix (refonte globale,
 * recette 3). Each tile takes the returned modifier with its key; [firstError] is the key of the
 * first tile in error, and the page scrolls each time [errors] changes.
 */
@Composable
fun <K : Any> scrollToFirstError(scroll: ScrollState, errors: Any?, firstError: K?): (K) -> Modifier {
    val tops = remember { mutableStateMapOf<K, Int>() }
    LaunchedEffect(errors) {
        val top = firstError?.let { tops[it] } ?: return@LaunchedEffect
        scroll.animateScrollTo(top)
    }
    return { key -> Modifier.onGloballyPositioned { tops[key] = it.positionInParent().y.toInt() } }
}
