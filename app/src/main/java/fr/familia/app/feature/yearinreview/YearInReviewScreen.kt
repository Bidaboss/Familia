package fr.familia.app.feature.yearinreview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/**
 * The story of the year, full screen: a tap on the right half goes on, on the left half goes back
 * (also two buttons for TalkBack). The last page closes it.
 */
@Composable
fun YearInReviewScreen(state: YearInReviewUiState, year: Int, onClose: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val pages = state.pages
    val last = (pages.size - 1).coerceAtLeast(0)
    val current = index.coerceIn(0, last)
    BackHandler(enabled = current > 0) { index = current - 1 }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (state.loading || pages.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Row(Modifier.fillMaxSize()) {
                    val none = remember { MutableInteractionSource() }
                    // TalkBack names each half, not only its action (accessibility test of #313).
                    val previous = stringResource(R.string.year_previous)
                    val next = stringResource(R.string.year_next)
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics { contentDescription = previous }
                            .clickable(interactionSource = none, indication = null, role = Role.Button, onClickLabel = previous) {
                                if (current > 0) index = current - 1
                            },
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .semantics { contentDescription = next }
                            .clickable(interactionSource = none, indication = null, role = Role.Button, onClickLabel = next) {
                                if (current < last) index = current + 1 else onClose()
                            },
                    )
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Progress(current, pages.size)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.year_page, current + 1, pages.size),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        IconButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.year_close))
                        }
                    }
                    // At 200 % a page can be taller than the screen: it scrolls, and a tap on its left or
                    // right half still turns the page (the halves behind it do not get the touch any more).
                    // TalkBack keeps « Précédent » and « Suivant » from the halves. On the last page a tap
                    // does not close the screen: « Fermer » and the cross do it, so a stray tap while
                    // scrolling at 200 % does not lose the page.
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().pointerInput(current, last) {
                            detectTapGestures { offset ->
                                if (offset.x < size.width / 2) {
                                    if (current > 0) index = current - 1
                                } else if (current < last) {
                                    index = current + 1
                                }
                            }
                        },
                    ) {
                        val pageHeight = maxHeight
                        val scroll = remember(current) { ScrollState(0) }
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = pageHeight),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Page(pages[current], year, onClose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val color = if (i <= current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun Page(page: ReviewPage, year: Int, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (page) {
            is ReviewPage.Intro -> {
                Big(stringResource(R.string.year_title, page.year))
                Body(stringResource(R.string.year_intro_body))
            }
            is ReviewPage.Numbers -> {
                Title(stringResource(R.string.year_numbers_title, year))
                if (page.goodNews > 0) Big(pluralStringResource(R.plurals.year_numbers_good_news, page.goodNews, page.goodNews))
                if (page.events > 0) Big(pluralStringResource(R.plurals.year_numbers_events, page.events, page.events))
                if (page.helps > 0) Big(pluralStringResource(R.plurals.year_numbers_helps, page.helps, page.helps))
            }
            is ReviewPage.Photos -> {
                Title(stringResource(R.string.year_photos_title))
                page.photos.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { photo ->
                            StorageImage(
                                photo.path,
                                stringResource(R.string.year_photo_by, photo.authorName),
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            is ReviewPage.TripOfTheYear -> {
                Title(stringResource(R.string.year_trip_title))
                Big(page.trip.title)
                Body(stringResource(R.string.year_trip_dates, page.trip.startDate.format(dayMonth), page.trip.endDate.format(dayMonth)))
            }
            is ReviewPage.Newcomers -> {
                Title(stringResource(R.string.year_newcomers_title))
                page.members.forEach { Big(it.firstName) }
            }
            is ReviewPage.Question -> {
                Title(stringResource(R.string.year_question_title))
                Body(page.question.text)
                page.answers.forEach { answer -> Body(stringResource(R.string.year_answer, answer.text.orEmpty(), answer.memberName)) }
            }
            ReviewPage.Thanks -> {
                Big(stringResource(R.string.year_thanks_title))
                Body(stringResource(R.string.year_thanks_body))
                Button(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.year_close)) }
            }
        }
    }
}

@Composable
private fun Big(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer)

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer)

@FamiliaPreviews
@Composable
private fun YearInReviewScreenPreview() {
    FamiliaTheme {
        YearInReviewScreen(
            YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Intro(2026), ReviewPage.Numbers(42, 12, 7), ReviewPage.Thanks)),
            year = 2026,
            onClose = {},
        )
    }
}
