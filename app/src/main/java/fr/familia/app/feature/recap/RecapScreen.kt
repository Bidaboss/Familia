package fr.familia.app.feature.recap

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.feature.tree.ChallengeGauge
import fr.familia.app.feature.trips.tripDatesLabel
import java.time.LocalDate
import java.time.ZoneId

/** Opened by the notification of Sunday (récit 36). */
@Composable
fun RecapRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    onOpenGoodNews: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
) {
    val flags = LocalFeatureFlags.current
    val parts = RecapParts(goodNews = flags.isOn(Feature.GoodNews), trips = flags.isOn(Feature.Trips), tree = flags.isOn(Feature.Tree))
    val today = LocalDate.now(ZoneId.of("Europe/Paris"))
    val viewModel: RecapViewModel = viewModel(
        key = "recap-$today-$parts",
        factory = viewModelFactory {
            initializer {
                RecapViewModel(
                    container?.goodNewsRepository,
                    container?.memberRepository,
                    container?.tripRepository,
                    container?.treeRepository,
                    currentMember.id,
                    parts,
                    today,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecapScreen(state, onBack, onOpenGoodNews, onOpenTrip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    state: RecapUiState,
    onBack: () -> Unit,
    onOpenGoodNews: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FamiliaTopBar(title = { Text(stringResource(R.string.recap_title), maxLines = 2, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onClick = onBack) })
        },
    ) { padding ->
        when (state) {
            RecapUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is RecapUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val recap = state.recap
                if (recap.isEmpty) {
                    item(key = "quiet") {
                        ClayCard(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.recap_quiet), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (recap.photos.isNotEmpty()) item(key = "photos") { PhotosCard(recap.photos, onOpenGoodNews) }
                if (recap.birthdays.isNotEmpty()) item(key = "birthdays") { BirthdaysCard(recap.birthdays, state.today) }
                recap.challenge?.let { challenge ->
                    item(key = "challenge") { ClayCard(Modifier.fillMaxWidth()) { ChallengeGauge(challenge) } }
                }
                if (recap.trips.isNotEmpty()) item(key = "trips") { TripsCard(recap.trips, state.today, onOpenTrip) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PhotosCard(photos: List<WeekPhoto>, onOpen: (String) -> Unit) {
    ClayCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.recap_photos))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val openLabel = stringResource(R.string.goodnews_open)
            photos.forEach { photo ->
                StorageImage(
                    photo.thumbnailPath,
                    contentDescription = openLabel,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = openLabel) { onOpen(photo.newsId) },
                )
            }
            // Fewer than three: the photos keep their size.
            repeat(WeekRecap.PHOTOS - photos.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BirthdaysCard(birthdays: List<Birthday>, today: LocalDate) {
    ClayCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.recap_birthdays))
        birthdays.forEach { birthday ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberAvatar(birthday.member)
                Column(Modifier.weight(1f)) {
                    Text(birthday.member.firstName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.birthday_when_age,
                            dayShortLabel(birthday.date, today),
                            pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
                    )
                }
            }
        }
    }
}

@Composable
private fun TripsCard(trips: List<Trip>, today: LocalDate, onOpen: (String) -> Unit) {
    ClayCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.recap_trips))
        val openLabel = stringResource(R.string.recap_trip_open)
        trips.forEach { trip ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = openLabel) { onOpen(trip.id) },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    tripDatesLabel(trip, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
                )
            }
        }
    }
}
