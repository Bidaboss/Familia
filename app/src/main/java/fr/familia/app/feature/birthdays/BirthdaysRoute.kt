package fr.familia.app.feature.birthdays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.R
import fr.familia.app.core.calendar.CalendarEntry
import fr.familia.app.core.calendar.addToCalendar
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects the Birthdays tab to its ViewModel. */
@Composable
fun BirthdaysRoute(container: AppContainer?) {
    val viewModel: BirthdaysViewModel = viewModel(
        factory = viewModelFactory { initializer { BirthdaysViewModel(container?.memberRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendarOn = LocalFeatureFlags.current.isOn(Feature.Calendar)
    BirthdaysScreen(
        state = state,
        onRetry = viewModel::retry,
        onAddToCalendar = if (calendarOn) {
            { birthday ->
                addToCalendar(
                    context,
                    CalendarEntry.forBirthday(
                        birthday,
                        context.getString(R.string.calendar_birthday_title, birthday.member.firstName),
                        context.getString(R.string.calendar_created_from),
                    ),
                )
            }
        } else {
            null
        },
    )
}
