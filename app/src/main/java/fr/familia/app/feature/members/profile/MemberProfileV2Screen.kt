package fr.familia.app.feature.members.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.viewas.LocalViewAs
import kotlinx.coroutines.launch

/** What the new page needs besides the old one: its data, and where its rows lead. */
data class ProfileV2Ui(
    val state: ProfileV2State?,
    val onOpenMember: (memberId: String) -> Unit = {},
    val onOpenEvent: (eventId: String) -> Unit = {},
    val onSavePhone: (String) -> Unit = {},
    val onSaveAddress: (String) -> Unit = {},
)

/**
 * « Une fiche de membre organisée » (PRD rév. 290, récit 66): one page with fixed sections, an empty
 * one hidden except on my own page, where it invites to fill it. The gifts keep their own rules:
 * nothing of what is given to someone shows on their page when they look (Mode présentation and
 * « Voir comme » included).
 */
@Composable
internal fun ContentV2(
    state: MemberProfileUiState.Ready,
    v2: ProfileV2Ui,
    onSetShown: (String, Boolean) -> Unit,
    onOpenRecipes: (() -> Unit)?,
    wishes: (@Composable (Member) -> Unit)?,
    card: (@Composable (Member) -> Unit)?,
    thinkOfMe: (@Composable (Member) -> Unit)?,
    gifts: (@Composable (Member) -> Unit)?,
) {
    val member = state.member
    val data = v2.state
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var wishesAt by remember { mutableIntStateOf(-1) }
    var thinkAt by remember { mutableIntStateOf(-1) }
    val today = data?.today ?: java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris"))
    val mayFill = state.isMe && LocalViewAs.current.viewed == null
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(
            state, data, today,
            onThinkOfMe = if (thinkOfMe != null && !state.isMe) ({ scope.launch { scroll.animateScrollTo(thinkAt.coerceAtLeast(0)) }; Unit }) else null,
            onWish = if (wishes != null && !state.isMe && member.birthDate?.let { daysUntilBirthday(it, today) } == 0) {
                { scope.launch { scroll.animateScrollTo(wishesAt.coerceAtLeast(0)) }; Unit }
            } else {
                null
            },
        )
        // On the day, and until the next birthday, the cards of vœux (récit 36): « Lui souhaiter » leads here.
        wishes?.let { slot -> Box(Modifier.onGloballyPositioned { wishesAt = it.positionInParent().y.toInt() }) { slot(member) } }
        // The only exception to the fixed order (PRODUIT-97).
        if (giftsFirst(member.birthDate, today)) {
            GiftsPart(member, gifts)
            card?.invoke(member)
        } else {
            card?.invoke(member)
            GiftsPart(member, gifts)
        }
        thinkOfMe?.let { slot -> Box(Modifier.onGloballyPositioned { thinkAt = it.positionInParent().y.toInt() }) { slot(member) } }
        data?.let { FamilyPart(it.kinship, state.isMe, v2.onOpenMember) }
        data?.let { MomentsPart(it, state.isMe, v2.onOpenEvent) }
        RecipesAndSouvenirs(state, onSetShown, onOpenRecipes)
        data?.let { CoordinatesPart(member, it, mayFill, v2.onSavePhone, v2.onSaveAddress) }
        if (state.saveFailed) {
            Text(stringResource(R.string.notifications_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A section that has nothing to show: only on my page, as an invitation. */
@Composable
private fun EmptyInvite(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: MemberProfileUiState.Ready,
    data: ProfileV2State?,
    today: java.time.LocalDate,
    onThinkOfMe: (() -> Unit)?,
    onWish: (() -> Unit)?,
) {
    val member = state.member
    val photo = LocalMemberPhotos.current[member.id]
    var fullPhoto by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val photoLabel = stringResource(R.string.profile_v2_photo_open, member.firstName)
        MemberAvatar(
            member,
            size = 112.dp,
            modifier = if (photo != null) Modifier.clip(RoundedCornerShape(56.dp)).clickable(onClickLabel = photoLabel) { fullPhoto = true } else Modifier,
        )
        Text(member.firstName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        state.householdName?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        member.birthDate?.let { birth ->
            val age = ageOn(birth, today)
            val days = daysUntilBirthday(birth, today)
            val whenText = if (days == 0) {
                stringResource(R.string.profile_v2_birthday_today)
            } else {
                stringResource(R.string.profile_v2_birthday_in, pluralStringResource(R.plurals.gifts_in_days, days, days))
            }
            Text(
                stringResource(R.string.profile_v2_age_and_birthday, pluralStringResource(R.plurals.birthday_age, age, age), whenText),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        data?.kinship?.let { kin ->
            relationLines(kin).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val phone = member.phone?.takeIf { hasCoordinates(member) && !state.isMe }
        val address = data?.household?.address?.takeIf { hasCoordinates(member) && !state.isMe }
        if (onThinkOfMe != null || onWish != null || phone != null || address != null) {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                onThinkOfMe?.let { RoundAction(Icons.Outlined.FavoriteBorder, stringResource(R.string.think_of_me_title), it) }
                onWish?.let { RoundAction(Icons.Outlined.Cake, stringResource(R.string.profile_v2_wish), it) }
                phone?.let { number ->
                    RoundAction(Icons.Outlined.Phone, stringResource(R.string.profile_v2_call)) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { c -> c.isDigit() || c == '+' }))) }
                    }
                }
                address?.let { place ->
                    RoundAction(Icons.Outlined.Directions, stringResource(R.string.profile_v2_directions)) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))) }
                    }
                }
            }
        }
    }
    if (fullPhoto && photo != null) {
        Dialog(onDismissRequest = { fullPhoto = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = stringResource(R.string.profile_v2_photo_close)) { fullPhoto = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(photo, contentDescription = stringResource(R.string.profile_v2_photo_of, member.firstName), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}

/** « Enfant de A et B », « Conjoint ou conjointe de C », « Parent de D, E et F ». */
@Composable
internal fun relationLines(kin: Kinship): List<String> = buildList {
    if (kin.parents.isNotEmpty()) add(stringResource(R.string.profile_v2_child_of, joinNames(kin.parents.map { it.name })))
    if (kin.partners.isNotEmpty()) add(stringResource(R.string.profile_v2_partner_of, joinNames(kin.partners.map { it.name })))
    if (kin.children.isNotEmpty()) add(stringResource(R.string.profile_v2_parent_of, joinNames(kin.children.map { it.name })))
}

/** « A », « A et B », « A, B et C ». */
@Composable
internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> stringResource(R.string.profile_v2_and, names.dropLast(1).joinToString(", "), names.last())
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** « Cadeaux »: their wishes, what I give them, and my notebook on my own page. */
@Composable
private fun GiftsPart(member: Member, gifts: (@Composable (Member) -> Unit)?) {
    val slot = gifts ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.profile_v2_gifts))
        slot(member)
    }
}

/** « Sa famille »: parents, partners, children from the tree; a member opens their page. */
@Composable
private fun FamilyPart(kin: Kinship, isMe: Boolean, onOpenMember: (String) -> Unit) {
    if (kin.isEmpty && !isMe) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(if (isMe) R.string.profile_v2_family_mine else R.string.profile_v2_family))
        if (kin.isEmpty) EmptyInvite(stringResource(R.string.profile_v2_family_empty))
        kin.parents.forEach { KinRow(it, stringResource(R.string.profile_v2_role_parent), onOpenMember) }
        kin.partners.forEach { KinRow(it, stringResource(R.string.profile_v2_role_partner), onOpenMember) }
        kin.children.forEach { KinRow(it, stringResource(R.string.profile_v2_role_child), onOpenMember) }
    }
}

@Composable
private fun KinRow(person: KinPerson, role: String, onOpenMember: (String) -> Unit) {
    val member = person.member
    ClayCard(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        onClick = member?.let { m -> { onOpenMember(m.id) } },
        onClickLabel = member?.let { stringResource(R.string.profile_v2_open_page, person.name) },
        contentPadding = ClaySpacing.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            member?.let {
                MemberAvatar(it)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                ClayCardTitle(person.name, style = MaterialTheme.typography.titleMedium)
                Text(role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** « Ses moments »: the next events they come to, and their last photos of Bonnes nouvelles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MomentsPart(data: ProfileV2State, isMe: Boolean, onOpenEvent: (String) -> Unit) {
    val empty = data.moments.isEmpty() && data.photos.isEmpty()
    if (empty && !isMe) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(if (isMe) R.string.profile_v2_moments_mine else R.string.profile_v2_moments))
        if (empty) EmptyInvite(stringResource(R.string.profile_v2_moments_empty))
        data.moments.forEach { event ->
            ClayCard(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = { onOpenEvent(event.id) },
                onClickLabel = stringResource(R.string.profile_v2_open_event),
                contentPadding = ClaySpacing.card,
            ) {
                ClayCardTitle(event.title, style = MaterialTheme.typography.titleMedium)
                Text(dayShortLabel(event.date, data.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (data.photos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.photos.forEach { news ->
                    StorageImage(
                        news.thumbnailPath(news.photoIds.first()),
                        stringResource(R.string.profile_v2_photo_news),
                        Modifier.width(96.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}

/** « Recettes et souvenirs » at the bottom: « Voir ses recettes » and the souvenirs they show. */
@Composable
private fun RecipesAndSouvenirs(state: MemberProfileUiState.Ready, onSetShown: (String, Boolean) -> Unit, onOpenRecipes: (() -> Unit)?) {
    val shown = state.souvenirs.any { it.shown }
    if (!state.isMe && onOpenRecipes == null && !shown) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.profile_v2_recipes_souvenirs))
        onOpenRecipes?.let { open ->
            OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.profile_recipes))
            }
        }
        if (state.isMe || shown) SouvenirsContent(state, onSetShown)
    }
}

/** « Coordonnées » (PRODUIT-98): a phone per adult, an address per household, never for a child. */
@Composable
private fun CoordinatesPart(member: Member, data: ProfileV2State, mayFill: Boolean, onSavePhone: (String) -> Unit, onSaveAddress: (String) -> Unit) {
    if (!hasCoordinates(member)) return
    val phone = member.phone
    val address = data.household?.address
    val mayAddress = mayFill && data.managesHousehold
    if (phone == null && address == null && !mayFill) return
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.profile_v2_coordinates))
        if (mayFill) EmptyInvite(stringResource(R.string.profile_v2_coordinates_help))
        phone?.let { Text(stringResource(R.string.profile_v2_phone_line, it), style = MaterialTheme.typography.bodyLarge) }
        if (mayFill) {
            ClayLink(stringResource(if (phone == null) R.string.profile_v2_phone_add else R.string.profile_v2_phone_edit), onClick = { editing = "phone" })
        }
        address?.let { Text(stringResource(R.string.profile_v2_address_line, it), style = MaterialTheme.typography.bodyLarge) }
        if (mayAddress) {
            ClayLink(stringResource(if (address == null) R.string.profile_v2_address_add else R.string.profile_v2_address_edit), onClick = { editing = "address" })
        }
        if (data.saveFailed) {
            Text(
                stringResource(R.string.profile_v2_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    when (editing) {
        "phone" -> CoordinateDialog(
            title = stringResource(R.string.profile_v2_phone_title),
            initial = phone.orEmpty(),
            keyboard = KeyboardType.Phone,
            valid = { it.isBlank() || isPhone(it.trim()) },
            onSave = { onSavePhone(it); editing = null },
            onDismiss = { editing = null },
        )
        "address" -> CoordinateDialog(
            title = stringResource(R.string.profile_v2_address_title),
            initial = address.orEmpty(),
            keyboard = KeyboardType.Text,
            valid = { it.length <= 200 },
            onSave = { onSaveAddress(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CoordinateDialog(title: String, initial: String, keyboard: KeyboardType, valid: (String) -> Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    isError = !valid(text),
                    supportingText = { Text(stringResource(R.string.profile_v2_coordinates_empty_removes)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = valid(text), modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.profile_v2_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.profile_v2_cancel)) }
        },
    )
}
