package fr.familia.app.feature.gooddeals.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.CategoryTiles
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.FormTopBar
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.feed.FeedTone
import fr.familia.app.feature.gooddeals.MAX_DEAL_DAYS
import fr.familia.app.feature.gooddeals.categoryLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** The form of a bon plan or of a bonne adresse (PRD, récit 45), in tiles (récit 78, DESIGN-51). Stateless. */
@Composable
fun GoodDealEditScreen(
    state: GoodDealEditUiState,
    onChange: ((GoodDealForm) -> GoodDealForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val editing = state as? GoodDealEditUiState.Editing
    val address = editing?.kind == GoodDealKind.Address
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FormTopBar(
                title = stringResource(
                    when {
                        editing?.isNew == false && address -> R.string.gooddeal_address_edit_title
                        editing?.isNew == false -> R.string.gooddeal_edit_title
                        address -> R.string.gooddeal_address_new_title
                        else -> R.string.gooddeal_new_title
                    },
                ),
                onBack = onBack,
            )
        },
        bottomBar = {
            if (editing != null) {
                StickyActionBar(
                    text = stringResource(if (editing.isNew) R.string.gooddeal_publish_family else R.string.gooddeal_save),
                    onClick = onSave,
                    busy = editing.isSaving,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodDealEditUiState.Loading, GoodDealEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GoodDealEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.LocalOffer,
                    title = stringResource(R.string.gooddeal_unavailable_title),
                    body = stringResource(R.string.gooddeal_unavailable_body),
                )
                is GoodDealEditUiState.Editing -> Form(state, onChange)
            }
        }
    }
}

/** The tiles of DESIGN-51, in their order on the screen. */
private enum class DealPart { Main, Category, Until, Where }

private fun GoodDealFormError.part(): DealPart = when (this) {
    GoodDealFormError.TitleMissing -> DealPart.Main
    GoodDealFormError.CategoryMissing -> DealPart.Category
    GoodDealFormError.EndInvalid -> DealPart.Until
    GoodDealFormError.LinkInvalid, GoodDealFormError.PhoneInvalid -> DealPart.Where
}

/** Lilac, the color of Entraide: dark on a light theme, light on a dark one. */
@Composable
private fun entraideAccent(): Color = if (LocalClayColors.current.tintAsStripe) FeedTone.Lilac.bar else FeedTone.Lilac.label

@Composable
private fun Form(state: GoodDealEditUiState.Editing, onChange: ((GoodDealForm) -> GoodDealForm) -> Unit) {
    val form = state.form
    val errors = state.errors
    val enabled = !state.isSaving
    val address = state.kind == GoodDealKind.Address
    val accent = entraideAccent()
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<DealPart, Int>() }
    // After a refused « Publier », the page goes up to the first tile to fix.
    LaunchedEffect(errors) {
        val first = errors.map { it.part() }.minOrNull() ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.part(part: DealPart) = onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NoSecretBanner()

        FormTile(
            if (address) Icons.Outlined.FavoriteBorder else Icons.Outlined.LocalOffer,
            stringResource(if (address) R.string.gooddeal_tile_address else R.string.gooddeal_tile_deal),
            Modifier.part(DealPart.Main),
            accent = accent,
        ) {
            FormLabel(stringResource(if (address) R.string.gooddeal_field_name else R.string.gooddeal_field_title))
            FormField(
                value = form.title,
                onValueChange = { v -> onChange { it.copy(title = v.take(80)) } },
                isError = GoodDealFormError.TitleMissing in errors,
                enabled = enabled,
                capitalize = true,
            )
            when {
                GoodDealFormError.TitleMissing in errors -> FormHint(stringResource(R.string.gooddeal_error_title_missing), isError = true)
                !address -> FormHint(stringResource(R.string.gooddeal_title_hint))
            }
            FormLabel(stringResource(if (address) R.string.gooddeal_field_why else R.string.gooddeal_field_text))
            FormField(
                value = form.text,
                onValueChange = { v -> onChange { it.copy(text = v.take(500)) } },
                enabled = enabled,
                capitalize = true,
                singleLine = false,
            )
            if (!address) {
                PhotoField(stored = state.storedPhoto, edit = form.photo, onEdit = { edit -> onChange { it.copy(photo = edit) } }, enabled = enabled)
            }
        }

        if (address) {
            FormTile(Icons.Outlined.Category, stringResource(R.string.gooddeal_field_category), Modifier.part(DealPart.Category), accent = accent) {
                CategoryTiles(
                    options = GoodDealCategory.entries,
                    selected = form.category,
                    onSelect = { category -> onChange { it.copy(category = category) } },
                    label = { categoryLabel(it) },
                    icon = { it.icon() },
                    enabled = enabled,
                    selectedColor = FeedTone.Lilac.labelBackground,
                    selectedContentColor = FeedTone.Lilac.label,
                )
                if (GoodDealFormError.CategoryMissing in errors) FormHint(stringResource(R.string.gooddeal_error_category), isError = true)
            }
        } else {
            FormTile(Icons.Outlined.Event, stringResource(R.string.gooddeal_tile_until), Modifier.part(DealPart.Until), accent = accent) {
                LastDayChips(form.lastDay, state.today, enabled) { day -> onChange { it.copy(lastDay = day) } }
                if (GoodDealFormError.EndInvalid in errors) {
                    FormHint(stringResource(R.string.gooddeal_error_end), isError = true)
                } else {
                    form.lastDay?.let { UntilSummary(it) }
                }
            }
        }

        FormTile(
            if (address) Icons.Outlined.Phone else Icons.Outlined.Place,
            stringResource(if (address) R.string.gooddeal_tile_reach else R.string.gooddeal_tile_where),
            Modifier.part(DealPart.Where),
            accent = accent,
        ) {
            FormLabel(stringResource(if (address) R.string.gooddeal_field_city else R.string.gooddeal_field_place))
            FormField(value = form.place, onValueChange = { v -> onChange { it.copy(place = v.take(80)) } }, enabled = enabled, capitalize = true)
            if (address) {
                FormLabel(stringResource(R.string.gooddeal_field_phone))
                FormField(
                    value = form.phone,
                    onValueChange = { v -> onChange { it.copy(phone = v.take(20)) } },
                    isError = GoodDealFormError.PhoneInvalid in errors,
                    enabled = enabled,
                    keyboardType = KeyboardType.Phone,
                )
                if (GoodDealFormError.PhoneInvalid in errors) FormHint(stringResource(R.string.gooddeal_error_phone), isError = true)
            }
            FormLabel(stringResource(R.string.gooddeal_field_link))
            FormField(
                value = form.link,
                onValueChange = { v -> onChange { it.copy(link = v.trim().take(500)) } },
                isError = GoodDealFormError.LinkInvalid in errors,
                enabled = enabled,
                keyboardType = KeyboardType.Uri,
            )
            FormHint(
                stringResource(
                    when {
                        GoodDealFormError.LinkInvalid in errors -> R.string.gooddeal_error_link
                        address -> R.string.gooddeal_address_link_hint
                        else -> R.string.gooddeal_link_hint
                    },
                ),
                isError = GoodDealFormError.LinkInvalid in errors,
            )
        }

        if (state.saveFailed) {
            FormHint(
                stringResource(R.string.gooddeal_save_failed),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean = false,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Shared with the whole family: no secret here (récit 45). A pale yellow banner with a shield. */
@Composable
private fun NoSecretBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BannerBackground, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = BannerText, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.gooddeal_no_secret_banner), style = MaterialTheme.typography.bodyMedium, color = BannerText)
    }
}

// Kept the same in light and dark, like the tones of the fil: the text sits on its own background.
private val BannerBackground = Color(0xFFFFF3CC)
private val BannerText = Color(0xFF5C4300)

/** « Jusqu'au dimanche 22 novembre, puis il disparaît tout seul », in lilac. */
@Composable
private fun UntilSummary(day: LocalDate) {
    Text(
        stringResource(R.string.gooddeal_until_summary, frenchDate(day, "EEEE d MMMM")),
        style = MaterialTheme.typography.bodyMedium,
        color = FeedTone.Lilac.label,
        modifier = Modifier
            .fillMaxWidth()
            .background(FeedTone.Lilac.labelBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Aujourd'hui, Ce week-end, Dans une semaine, and « Autre jour », which opens a calendar from today
 * to 3 months on, the days the rules accept. A day of the calendar shows as its own pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LastDayChips(day: LocalDate?, today: LocalDate, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val quick = dealDayOf(day, today)
    ChipGroup {
        DealDay.entries.forEach { choice ->
            FormChip(
                label = stringResource(
                    when (choice) {
                        DealDay.Today -> R.string.day_today
                        DealDay.Weekend -> R.string.gooddeal_day_weekend
                        DealDay.InAWeek -> R.string.gooddeal_day_week
                    },
                ),
                selected = quick == choice,
                onClick = { onPick(choice.dayFrom(today)) },
                enabled = enabled,
            )
        }
        if (day != null && quick == null) {
            FormChip(
                label = frenchDate(day, "EEE d MMM").replaceFirstChar { it.titlecase(Locale.FRANCE) },
                selected = true,
                onClick = { open = true },
                description = frenchDate(day, "EEEE d MMMM"),
                enabled = enabled,
            )
        }
        FormChip(
            label = stringResource(R.string.form_other_day),
            selected = false,
            onClick = { open = true },
            icon = Icons.Outlined.CalendarMonth,
            enabled = enabled,
        )
    }
    if (open) {
        val firstMillis = today.toUtcMillis()
        val lastMillis = today.plusDays(MAX_DEAL_DAYS).toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: today).toUtcMillis(),
            yearRange = today.year..today.plusDays(MAX_DEAL_DAYS).year,
            selectableDates = remember(firstMillis, lastMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstMillis..lastMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun GoodDealCategory.icon(): ImageVector = when (this) {
    GoodDealCategory.Health -> Icons.Outlined.HealthAndSafety
    GoodDealCategory.Home -> Icons.Outlined.Handyman
    GoodDealCategory.Car -> Icons.Outlined.DirectionsCar
    GoodDealCategory.Kids -> Icons.Outlined.ChildCare
    GoodDealCategory.Outings -> Icons.Outlined.Restaurant
    GoodDealCategory.Other -> Icons.Outlined.MoreHoriz
}

@FamiliaPreviews
@Composable
private fun GoodDealEditScreenPreview() {
    val today = LocalDate.of(2026, 11, 20)
    FamiliaTheme {
        Surface {
            GoodDealEditScreen(
                GoodDealEditUiState.Editing(GoodDealForm(title = "Fraises à cueillir", lastDay = today.plusDays(2)), isNew = true, today = today),
                {}, {}, {},
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun GoodAddressEditScreenPreview() {
    val today = LocalDate.of(2026, 11, 20)
    FamiliaTheme {
        Surface {
            GoodDealEditScreen(
                GoodDealEditUiState.Editing(
                    GoodDealForm(title = "Garage du Pont", category = GoodDealCategory.Car),
                    isNew = true,
                    kind = GoodDealKind.Address,
                    today = today,
                ),
                {}, {}, {},
            )
        }
    }
}
