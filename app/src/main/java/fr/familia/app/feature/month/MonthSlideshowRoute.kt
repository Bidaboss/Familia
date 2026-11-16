package fr.familia.app.feature.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumFilmPart
import fr.familia.app.core.album.AlbumFilmViewModel
import fr.familia.app.core.album.AlbumPhotosUiState
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** « Novembre », the month alone, as said in French. */
fun monthName(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** The card of the Accueil, from the last Sunday of the month. */
@Composable
fun MonthSlideshowHomeCard(month: YearMonth, onOpen: () -> Unit) {
    val tint = ClayTint.Plain
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        Text(
            stringResource(R.string.month_title, monthName(month)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.month_home_body), style = MaterialTheme.typography.bodyLarge, color = FamiliaClay.colors.onTintVariant(tint))
        OutlinedButton(onClick = onOpen, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.month_home_open)) }
    }
}

/** The slideshow of [month]: its photos, its birthdays, and the video to watch or share. */
@Composable
fun MonthSlideshowRoute(container: AppContainer?, currentMember: Member, month: YearMonth, onBack: () -> Unit) {
    val viewModel: MonthSlideshowViewModel = viewModel(
        key = "month-$month",
        factory = viewModelFactory {
            initializer {
                MonthSlideshowViewModel(
                    trips = container?.tripRepository,
                    events = container?.eventRepository,
                    albums = container?.albumPhotoRepository,
                    members = container?.memberRepository,
                    currentMemberId = currentMember.id,
                    month = month,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filmsDir = File(LocalContext.current.cacheDir, "films")
    val filmViewModel: AlbumFilmViewModel = viewModel(
        key = "month-film-$month",
        factory = viewModelFactory {
            initializer { AlbumFilmViewModel(container?.photoStorage, container?.filmMaker, "mois-$month", filmsDir) }
        },
    )
    val film by filmViewModel.uiState.collectAsStateWithLifecycle()
    val filmActions = remember(filmViewModel) {
        FilmActions(onMake = { filmViewModel.make(viewModel.uiState.value.photos) }, onCancel = filmViewModel::cancel)
    }
    MonthSlideshowScreen(month, state, film, filmActions, onBack)
}

/** Stateless. Nobody adds or removes a photo here: that happens in each album. */
@Composable
fun MonthSlideshowScreen(month: YearMonth, state: MonthSlideshowUiState, film: FilmState, filmActions: FilmActions, onBack: () -> Unit) {
    AlbumScreen(
        title = stringResource(R.string.month_title, monthName(month)),
        // No member here, so the viewer offers no « Retirer ».
        state = AlbumPhotosUiState(photos = state.photos),
        actions = PhotoActions(),
        onBack = onBack,
        actionsBar = { HelpButton(ScreenHelp.MonthSlideshow) },
        top = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.birthdays.isNotEmpty()) MonthBirthdays(state.birthdays)
                if (!state.loading && state.photos.isEmpty()) {
                    Text(stringResource(R.string.month_empty), style = MaterialTheme.typography.bodyLarge)
                } else {
                    AlbumFilmPart(film, state.photos.size, filmActions)
                }
            }
        },
    )
}

@Composable
private fun MonthBirthdays(birthdays: List<Member>) {
    val tint = ClayTint.Birthday
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        Text(stringResource(R.string.month_birthdays), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        birthdays.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberAvatar(member, size = 40.dp)
                Column {
                    Text(member.firstName, style = MaterialTheme.typography.bodyLarge)
                    member.birthDate?.let { day ->
                        Text(
                            if (day.dayOfMonth == 1) stringResource(R.string.month_birthday_first) else stringResource(R.string.month_birthday_day, day.dayOfMonth),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FamiliaClay.colors.onTintVariant(tint),
                        )
                    }
                }
            }
        }
    }
}

/** Fictional month for previews and screenshots. */
fun previewMonthSlideshow(birthdays: List<Member> = emptyList()): MonthSlideshowUiState =
    MonthSlideshowUiState(loading = false, photos = previewAlbumPhotos(count = 9).photos, birthdays = birthdays)

@FamiliaPreviews
@Composable
private fun MonthSlideshowPreview() {
    FamiliaTheme { Surface { MonthSlideshowScreen(YearMonth.of(2027, 8), previewMonthSlideshow(), FilmState.Idle, FilmActions(), {}) } }
}

@FamiliaPreviews
@Composable
private fun MonthSlideshowHomeCardPreview() {
    FamiliaTheme { Surface { MonthSlideshowHomeCard(YearMonth.of(2027, 8)) {} } }
}
