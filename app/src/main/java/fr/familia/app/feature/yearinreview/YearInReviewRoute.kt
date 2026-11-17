package fr.familia.app.feature.yearinreview

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.home.HomeChip
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The story of [year] (récit 55). */
@Serializable
data class YearInReviewDestination(val year: Int)

@Composable
fun YearInReviewRoute(container: AppContainer?, currentMember: Member, year: Int, onBack: () -> Unit) {
    val viewModel: YearInReviewViewModel = viewModel(
        key = "year-$year",
        factory = viewModelFactory {
            initializer {
                YearInReviewViewModel(
                    members = container?.memberRepository,
                    goodNews = container?.goodNewsRepository,
                    events = container?.eventRepository,
                    helps = container?.helpRequestRepository,
                    trips = container?.tripRepository,
                    questions = container?.weeklyQuestionRepository,
                    currentMemberId = currentMember.id,
                    year = year,
                    today = familyToday(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    YearInReviewScreen(state, year, onClose = onBack)
}

/**
 * The year that can be opened on [today], or null: the module must be on, and the admin principal
 * tries this year at any time with a simulated date (PRODUIT, 17/11), from the drawer. The chip of
 * the Accueil ([forCard]) keeps to 26/12–10/01 for everyone, the admin principal too (PRD rév. 259).
 */
@Composable
fun openableReviewYear(today: LocalDate, forCard: Boolean): Int? {
    val flags = LocalFeatureFlags.current
    if (!flags.isOn(Feature.YearInReview)) return null
    val simulate = flags.isOwner
    if (forCard && !reviewCardShows(today)) return null
    return reviewYear(today, simulate)
}

/** The chip of the Accueil, from 26/12 to 10/01: « Notre année 2026 ». For the home screen to place. */
@Composable
fun YearInReviewHomeChip(today: LocalDate, onOpen: (Int) -> Unit) {
    val year = openableReviewYear(today, forCard = true) ?: return
    HomeChip(stringResource(R.string.year_title, year), onClick = { onOpen(year) })
}

/** « Revoir notre année », in Bonnes nouvelles, all the next year long. */
@Composable
fun YearInReviewAgainButton(today: LocalDate, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val year = openableReviewYear(today, forCard = false) ?: return
    OutlinedButton(onClick = { onOpen(year) }, modifier = modifier.heightIn(min = 56.dp)) {
        Text(stringResource(R.string.year_again, year))
    }
}
