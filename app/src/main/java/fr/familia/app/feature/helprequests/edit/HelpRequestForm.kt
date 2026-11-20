package fr.familia.app.feature.helprequests.edit

import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.photo.PhotoEdit
import java.time.LocalDate
import java.time.LocalTime

/** What is being typed in the request form. */
data class HelpRequestForm(
    val kind: HelpKind = HelpKind.Childcare,
    val title: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val place: String = "",
    val note: String = "",
    /** The members named « @Prénom » in [note] (Kévin, 19/11). */
    val mentionIds: List<String> = emptyList(),
    val forMemberId: String = "",
    /** The people ticked, without those who are always warned (see [HelpRequestEditUiState.Editing.lockedIds]). */
    val notifyIds: Set<String> = emptySet(),
    /** What was done with the photo since the form opened (Kévin 16/11). */
    val photo: PhotoEdit = PhotoEdit.Unchanged,
    /** « Où » like the events (récit 74, Kévin 20/11): [place] is the text of « Autre » or « Adresse », or the household's name. */
    val placeKind: PlaceKind = PlaceKind.Other,
    val placeHouseholdId: String? = null,
) {
    /** Another choice of « Où »: the name of a household never stays as a text to edit. */
    fun withPlaceKind(kind: PlaceKind): HelpRequestForm = if (kind == placeKind) this else copy(
        placeKind = kind,
        placeHouseholdId = null,
        place = if (placeKind == PlaceKind.Household || kind == PlaceKind.Household) "" else place,
    )

    /** « Chez un foyer »: [household] chosen, its name is the text every screen shows. */
    fun atHousehold(household: Household): HelpRequestForm =
        copy(placeKind = PlaceKind.Household, placeHouseholdId = household.id, place = household.name)

    /** What is saved: no text, no place at all; a household is kept only with its id. */
    fun savedPlace(): Triple<String?, PlaceKind, String?> {
        val text = place.trim().ifEmpty { null } ?: return Triple(null, PlaceKind.Other, null)
        return when {
            placeKind == PlaceKind.Household && placeHouseholdId == null -> Triple(text, PlaceKind.Other, null)
            placeKind == PlaceKind.Household -> Triple(text, PlaceKind.Household, placeHouseholdId)
            else -> Triple(text, placeKind, null)
        }
    }

    companion object {
        /** [keepDate] is false for « Dupliquer », which asks for another date (carte PRODUIT-23). */
        fun from(request: HelpRequest, keepDate: Boolean = true) = HelpRequestForm(
            kind = request.kind,
            title = request.title,
            date = request.date.takeIf { keepDate },
            startTime = request.startTime,
            endTime = request.endTime,
            place = request.place.orEmpty(),
            note = request.note.orEmpty(),
            mentionIds = request.mentionIds,
            forMemberId = request.forMemberId,
            notifyIds = request.notifyIds.toSet(),
            // An old request with a free-text place opens on « Autre ».
            placeKind = request.placeKind,
            placeHouseholdId = request.placeHouseholdId,
        )
    }
}

enum class HelpFormError {
    TitleMissing, TitleTooLong, DateMissing, DatePast, EndBeforeStart, PlaceTooLong, NoteTooLong, NobodyWarned,
}

const val HELP_MAX_TITLE = 80
const val HELP_MAX_PLACE = 120
const val HELP_MAX_NOTE = 1000
const val HELP_MAX_WARNED = 30

/** Same limits as the security rules (récits 13 and 14). [warned] counts everyone who will be warned. */
fun validate(form: HelpRequestForm, warned: Int, today: LocalDate, dateUnchanged: Boolean): Set<HelpFormError> = buildSet {
    val title = form.title.trim()
    if (title.isEmpty()) add(HelpFormError.TitleMissing)
    if (title.length > HELP_MAX_TITLE) add(HelpFormError.TitleTooLong)
    when {
        form.date == null -> add(HelpFormError.DateMissing)
        form.date < today && !dateUnchanged -> add(HelpFormError.DatePast)
    }
    if (form.startTime != null && form.endTime != null && form.endTime <= form.startTime) add(HelpFormError.EndBeforeStart)
    if (form.place.trim().length > HELP_MAX_PLACE) add(HelpFormError.PlaceTooLong)
    if (form.note.trim().length > HELP_MAX_NOTE) add(HelpFormError.NoteTooLong)
    if (warned == 0 || warned > HELP_MAX_WARNED) add(HelpFormError.NobodyWarned)
}
