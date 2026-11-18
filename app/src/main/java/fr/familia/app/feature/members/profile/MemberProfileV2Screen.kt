package fr.familia.app.feature.members.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextAlign
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
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.ui.memberColor
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.members.photo.MemberPhotoEditor
import kotlinx.coroutines.launch

/** What the new page needs besides the old one: its data, and where its rows lead. */
data class ProfileV2Ui(
    val state: ProfileV2State?,
    val onOpenMember: (memberId: String) -> Unit = {},
    val onOpenEvent: (eventId: String) -> Unit = {},
    val onSavePhone: (String) -> Unit = {},
    val onSaveAddress: (String) -> Unit = {},
    /** « Changer ma photo » on the avatar of my page (P3); null hides the button. */
    val onSavePhoto: ((ByteArray) -> Unit)? = null,
    val onRemovePhoto: () -> Unit = {},
)

/**
 * « Une fiche de membre organisée » (PRD rév. 290, récit 66), redrawn after the maquettes P1 à P5
 * (PRODUIT-114 à 116): a band of the member's color, the round photo over it, then one Argile card
 * per section. An empty section is hidden, except on my own page, where it invites to fill it. The
 * gifts keep their own rules: nothing of what is given to someone shows on their page when they look
 * (Mode présentation and « Voir comme » included).
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
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Header(
            state, data, today, mayFill,
            onSavePhoto = v2.onSavePhoto?.takeIf { mayFill },
            onRemovePhoto = v2.onRemovePhoto,
            onThinkOfMe = if (thinkOfMe != null && !state.isMe) ({ scope.launch { scroll.animateScrollTo(thinkAt.coerceAtLeast(0)) }; Unit }) else null,
            onWish = if (wishes != null && !state.isMe) ({ scope.launch { scroll.animateScrollTo(wishesAt.coerceAtLeast(0)) }; Unit }) else null,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (mayFill && data != null) FillGauge(member, data, state.souvenirs.any { it.shown })
            // On the day, and until the next birthday, the cards of vœux (récit 36): « Vœux » leads here.
            wishes?.let { slot -> Box(Modifier.onGloballyPositioned { wishesAt = it.positionInParent().y.toInt() }) { slot(member) } }
            val tastes: @Composable () -> Unit = { card?.let { slot -> CompositionLocalProvider(LocalProfileSection provides true) { slot(member) } } }
            // The only exception to the fixed order (PRODUIT-97).
            if (giftsFirst(member.birthDate, today)) {
                GiftsPart(member, state.isMe, today, gifts)
                tastes()
            } else {
                tastes()
                GiftsPart(member, state.isMe, today, gifts)
            }
            thinkOfMe?.let { slot -> Box(Modifier.onGloballyPositioned { thinkAt = it.positionInParent().y.toInt() }) { slot(member) } }
            data?.let { FamilyPart(it.kinship, state.isMe, v2.onOpenMember) }
            data?.let { MomentsPart(it, state.isMe, v2.onOpenEvent) }
            RecipesAndSouvenirs(state, data, onSetShown, onOpenRecipes)
            data?.let { CoordinatesPart(member, it, state.isMe, mayFill, v2.onSavePhone, v2.onSaveAddress) }
            data?.let { if (!state.isMe) ReachThroughParents(member, it.kinship) }
            if (state.saveFailed) {
                Text(stringResource(R.string.notifications_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A sentence inside a section that has nothing to show yet: only on my page. */
@Composable
private fun EmptyInvite(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The top of the page (PRODUIT-114): a band of the member's color with pale bubbles, the round photo
 * of 108 dp over its edge, the first name, the age and the birthday, a pill « Anniversaire dans N
 * jours », then the round buttons. None on my own page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: MemberProfileUiState.Ready,
    data: ProfileV2State?,
    today: java.time.LocalDate,
    mine: Boolean,
    onSavePhoto: ((ByteArray) -> Unit)?,
    onRemovePhoto: () -> Unit,
    onThinkOfMe: (() -> Unit)?,
    onWish: (() -> Unit)?,
) {
    val member = state.member
    val photo = LocalMemberPhotos.current[member.id]
    var fullPhoto by rememberSaveable { mutableStateOf(false) }
    var changePhoto by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val color = memberColor(member)
    val soon = birthdaySoon(member.birthDate, today)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(color)
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = size.height * 0.9f, center = Offset(size.width * 0.9f, size.height * 0.1f))
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.6f, center = Offset(size.width * 0.15f, size.height * 1.1f))
                },
        )
        Column(
            Modifier.fillMaxWidth().padding(top = BAND_HEIGHT - AVATAR_SIZE / 2, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                val photoLabel = stringResource(R.string.profile_v2_photo_open, member.firstName)
                Box(
                    Modifier
                        .border(3.dp, color, CircleShape)
                        .padding(3.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp),
                ) {
                    MemberAvatar(
                        member,
                        size = AVATAR_SIZE - 14.dp,
                        modifier = if (photo != null) Modifier.clip(CircleShape).clickable(onClickLabel = photoLabel) { fullPhoto = true } else Modifier,
                    )
                }
                if (onSavePhoto != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.profile_v2_photo_change)) { changePhoto = true },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = stringResource(R.string.profile_v2_photo_change),
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                        }
                    }
                }
            }
            Text(member.firstName, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            member.birthDate?.let { birth ->
                val age = pluralStringResource(R.plurals.birthday_age, ageOn(birth, today), ageOn(birth, today))
                Text(
                    stringResource(R.string.profile_v2_age_line, age, birthdayLabel(birth)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BirthdayPill(daysUntilBirthday(birth, today), soon)
            }
            val phone = member.phone?.takeIf { hasCoordinates(member) && !state.isMe }
            val address = data?.household?.address?.takeIf { hasCoordinates(member) && !state.isMe }
            if (onThinkOfMe != null || onWish != null || phone != null || address != null) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    onWish?.let { RoundAction(Icons.Outlined.Cake, stringResource(R.string.profile_v2_wishes_button), dark = soon, onClick = it) }
                    onThinkOfMe?.let { RoundAction(Icons.Outlined.FavoriteBorder, stringResource(R.string.think_of_me_title), onClick = it) }
                    phone?.let { number -> RoundAction(Icons.Outlined.Phone, stringResource(R.string.profile_v2_call)) { dial(context, number) } }
                    address?.let { place -> RoundAction(Icons.Outlined.Directions, stringResource(R.string.profile_v2_directions)) { directions(context, place) } }
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
    if (changePhoto && onSavePhoto != null) {
        MemberPhotoEditor(
            member = member,
            hasPhoto = photo != null,
            onSave = onSavePhoto,
            onRemove = onRemovePhoto,
            onDismiss = { changePhoto = false },
        )
    }
}

private val BAND_HEIGHT = 112.dp
private val AVATAR_SIZE = 108.dp

/** « Anniversaire dans 114 jours », dark in the 30 days before (P2). */
@Composable
private fun BirthdayPill(days: Int, dark: Boolean) {
    val text = if (days == 0) {
        stringResource(R.string.profile_v2_birthday_pill_today)
    } else {
        stringResource(R.string.profile_v2_birthday_pill, pluralStringResource(R.plurals.gifts_in_days, days, days))
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (dark) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surface,
        contentColor = if (dark) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        border = if (dark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Cake, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A round button of 56 dp with its label under it: Vœux, Appeler, Itinéraire. */
@Composable
private fun RoundAction(icon: ImageVector, label: String, dark: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (dark) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surface,
            border = if (dark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (dark) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun dial(context: android.content.Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { c -> c.isDigit() || c == '+' }))) }
}

private fun directions(context: android.content.Context, place: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))) }
}

/**
 * « Ta fiche est remplie à 70 % » (PRODUIT-116), with what is missing. Gone once everything is there.
 * See [fillParts] for what counts.
 */
@Composable
private fun FillGauge(member: Member, data: ProfileV2State, souvenirShown: Boolean) {
    val parts = fillParts(member, LocalMemberPhotos.current[member.id] != null, data, souvenirShown)
    val percent = fillPercent(parts)
    if (percent == 100) return
    val missing = parts.filter { !it.second }.map { fillPartLabel(it.first) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_v2_fill_title, percent), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { percent / 100f },
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Text(
                stringResource(R.string.profile_v2_fill_missing, joinNames(missing)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun fillPartLabel(part: FillPart): String = stringResource(
    when (part) {
        FillPart.Photo -> R.string.profile_v2_fill_photo
        FillPart.Tastes -> R.string.profile_v2_fill_tastes
        FillPart.Wishes -> R.string.profile_v2_fill_wishes
        FillPart.Family -> R.string.profile_v2_fill_family
        FillPart.RecipeOrSouvenir -> R.string.profile_v2_fill_recipe
        FillPart.Phone -> R.string.profile_v2_fill_phone
    },
)

/**
 * « Cadeaux »: their wishes, what I give them, and my notebook on my own page. In the 30 days before
 * someone else's birthday: « Bientôt » and a dark band (P2).
 */
@Composable
private fun GiftsPart(member: Member, isMe: Boolean, today: java.time.LocalDate, gifts: (@Composable (Member) -> Unit)?) {
    val slot = gifts ?: return
    val days = member.birthDate?.takeIf { !isMe && birthdaySoon(it, today) }?.let { daysUntilBirthday(it, today) }
    ProfileSectionCard(
        title = stringResource(R.string.profile_v2_gifts),
        icon = Icons.Outlined.CardGiftcard,
        accent = SectionAccent.Peach,
        badge = days?.let { stringResource(R.string.profile_v2_soon) },
    ) {
        days?.let { d ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                    Text(
                        if (d == 0) {
                            stringResource(R.string.profile_v2_birthday_band_today)
                        } else {
                            stringResource(R.string.profile_v2_birthday_band, pluralStringResource(R.plurals.gifts_in_days, d, d))
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
        CompositionLocalProvider(LocalProfileSection provides true) { slot(member) }
    }
}

/** « Sa famille »: parents, partners, children from the tree, side by side; a member opens their page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyPart(kin: Kinship, isMe: Boolean, onOpenMember: (String) -> Unit) {
    if (kin.isEmpty && !isMe) return
    ProfileSectionCard(
        title = stringResource(if (isMe) R.string.profile_v2_family_mine else R.string.profile_v2_family),
        icon = Icons.Outlined.Groups,
        accent = SectionAccent.Lilac,
    ) {
        if (kin.isEmpty) EmptyInvite(stringResource(R.string.profile_v2_family_empty))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            kin.partners.forEach { KinChip(it, stringResource(R.string.profile_v2_role_partner), onOpenMember) }
            kin.children.forEach { KinChip(it, stringResource(R.string.profile_v2_role_child), onOpenMember) }
            kin.parents.forEach { KinChip(it, stringResource(R.string.profile_v2_role_parent), onOpenMember) }
        }
        if (kin.parents.isNotEmpty()) {
            Text(stringResource(R.string.profile_v2_child_of, joinNames(kin.parents.map { it.name })), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** A relative: their avatar, first name and role; a member of the app opens their page. */
@Composable
private fun KinChip(person: KinPerson, role: String, onOpenMember: (String) -> Unit) {
    val member = person.member
    val label = member?.let { stringResource(R.string.profile_v2_open_page, person.name) }
    Column(
        Modifier
            .widthIn(min = 72.dp, max = 112.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (member != null) Modifier.clickable(onClickLabel = label) { onOpenMember(member.id) } else Modifier)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (member != null) {
            MemberAvatar(member, size = 56.dp)
        } else {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Text(person.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(person.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** « Ses moments »: the next events they come to, and their last photos of Bonnes nouvelles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MomentsPart(data: ProfileV2State, isMe: Boolean, onOpenEvent: (String) -> Unit) {
    val empty = data.moments.isEmpty() && data.photos.isEmpty()
    if (empty && !isMe) return
    ProfileSectionCard(
        title = stringResource(if (isMe) R.string.profile_v2_moments_mine else R.string.profile_v2_moments),
        icon = Icons.Outlined.PhotoCamera,
        accent = SectionAccent.Green,
    ) {
        if (empty) EmptyInvite(stringResource(R.string.profile_v2_moments_empty))
        data.moments.forEach { event ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = stringResource(R.string.profile_v2_open_event)) { onOpenEvent(event.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateBox(event.date)
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text(dayShortLabel(event.date, data.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (data.photos.isNotEmpty()) {
            ProfileSectionLabel(stringResource(R.string.profile_v2_good_news))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.photos.forEach { news ->
                    StorageImage(
                        news.thumbnailPath(news.photoIds.first()),
                        stringResource(R.string.profile_v2_photo_news),
                        Modifier.width(88.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}

/** « SAM 28 NOV. » in a small box, as on the planning. */
@Composable
private fun DateBox(date: java.time.LocalDate) {
    Column(
        Modifier.widthIn(min = 48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(frenchDate(date, "EEE").uppercase(), style = MaterialTheme.typography.labelSmall)
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
        Text(frenchDate(date, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

/** « Recettes et souvenirs »: « Voir ses recettes » and the souvenirs they show; an invitation on my empty page. */
@Composable
private fun RecipesAndSouvenirs(state: MemberProfileUiState.Ready, data: ProfileV2State?, onSetShown: (String, Boolean) -> Unit, onOpenRecipes: (() -> Unit)?) {
    val shown = state.souvenirs.any { it.shown }
    if (!state.isMe && onOpenRecipes == null && !shown) return
    val title = stringResource(if (state.isMe) R.string.profile_v2_recipes_souvenirs_mine else R.string.profile_v2_recipes_souvenirs)
    if (state.isMe && onOpenRecipes != null && state.souvenirs.isEmpty() && data?.hasRecipe != true) {
        ProfileInviteCard(
            title = title,
            icon = Icons.Outlined.Description,
            accent = SectionAccent.Green,
            text = stringResource(R.string.profile_v2_recipes_invite),
            action = stringResource(R.string.profile_v2_recipes_add),
            onAction = onOpenRecipes,
        )
        return
    }
    ProfileSectionCard(title = title, icon = Icons.Outlined.Description, accent = SectionAccent.Green) {
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
private fun CoordinatesPart(
    member: Member,
    data: ProfileV2State,
    isMe: Boolean,
    mayFill: Boolean,
    onSavePhone: (String) -> Unit,
    onSaveAddress: (String) -> Unit,
) {
    if (!hasCoordinates(member)) return
    val phone = member.phone
    val address = data.household?.address
    val mayAddress = mayFill && data.managesHousehold
    if (phone == null && address == null && !mayFill) return
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    ProfileSectionCard(
        title = stringResource(if (isMe) R.string.profile_v2_coordinates_mine else R.string.profile_v2_coordinates),
        icon = Icons.Outlined.Phone,
        accent = SectionAccent.Lilac,
        onEdit = if (mayFill) ({ editing = "phone" }) else null,
        editLabel = stringResource(if (phone == null) R.string.profile_v2_phone_add else R.string.profile_v2_phone_edit),
    ) {
        if (mayFill && phone == null && address == null) EmptyInvite(stringResource(R.string.profile_v2_coordinates_help))
        phone?.let {
            CoordinateRow(it, stringResource(R.string.profile_v2_phone_of, member.firstName), action = if (isMe) null else stringResource(R.string.profile_v2_call)) {
                dial(context, it)
            }
        }
        if (mayFill && phone == null) {
            ClayLink(stringResource(R.string.profile_v2_phone_add), onClick = { editing = "phone" })
        }
        address?.let {
            CoordinateRow(it, stringResource(R.string.profile_v2_address_of), action = if (isMe) null else stringResource(R.string.profile_v2_directions)) {
                directions(context, it)
            }
        }
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

/** « 06 12 34 56 78 / Téléphone de Hugo » with « Appeler » on its right; it wraps rather than cuts at 130 %. */
@Composable
private fun CoordinateRow(value: String, caption: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let {
            FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(it) }
        }
    }
}

/**
 * A child has no coordinates (PRODUIT-98, P4): « Pour joindre Noé, passe par ses parents », with
 * « Appeler » for each parent who gave a phone. Nothing when none did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReachThroughParents(member: Member, kin: Kinship) {
    if (hasCoordinates(member)) return
    val parents = kin.parents.mapNotNull { p -> p.member?.takeIf { hasCoordinates(it) }?.phone?.let { phone -> p.name to phone } }
    if (parents.isEmpty()) return
    val context = LocalContext.current
    ClayCard(Modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
        Text(stringResource(R.string.profile_v2_reach_parents, member.firstName), style = MaterialTheme.typography.bodyLarge)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parents.forEach { (name, phone) ->
                FilledTonalButton(onClick = { dial(context, phone) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.profile_v2_call_name, name))
                }
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
