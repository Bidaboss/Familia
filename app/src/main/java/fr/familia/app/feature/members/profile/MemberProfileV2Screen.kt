package fr.familia.app.feature.members.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.members.photo.MemberPhotoEditor
import fr.familia.app.feature.members.photo.MemberPhotoViewer
import fr.familia.app.feature.souvenirs.SouvenirKind

/** What the page needs besides the member: its data, and where its rows lead. */
data class ProfileV2Ui(
    val state: ProfileV2State?,
    val onOpenEvent: (eventId: String) -> Unit = {},
    val onSavePhone: (String) -> Unit = {},
    val onSaveAddress: (String) -> Unit = {},
    /** « Changer ma photo » on the avatar of my page (P3); null hides the button. */
    val onSavePhoto: ((ByteArray) -> Unit)? = null,
    val onRemovePhoto: () -> Unit = {},
    /** Saves the card (« À savoir », tastes, « En ce moment »); false when a field is too long. */
    val onSaveCard: (MemberCard) -> Boolean = { false },
    /** « Ses envies (4) », while Cadeaux is on. */
    val onOpenWishes: (() -> Unit)? = null,
    /** « Préparer une surprise » (never on my page nor in « Voir comme »). */
    val onPrepareSurprise: (() -> Unit)? = null,
    /** « Voir ses recettes », while the recipes are on. */
    val onOpenRecipes: (() -> Unit)? = null,
    /** « Ajouter une recette », on my own page only (DESIGN-172). */
    val onAddRecipe: (() -> Unit)? = null,
)

/**
 * The member page (brief Famille-recettes, F6 à F8, prototype v157): lying on the photo of the theme,
 * the round photo, the first name, the surnom and the birthday, « En ce moment », then one glass card
 * per section in a fixed order (règle 7): À savoir, Goûts et passions, Cadeaux, Recettes, Moments,
 * Réussites, Actualités, Coordonnées. An empty section is hidden on someone else's page. Nothing of
 * what is given to someone shows on their page (« Voir comme » included).
 */
@Composable
internal fun ContentV2(
    state: MemberProfileUiState.Ready,
    v2: ProfileV2Ui,
    /** The cards of vœux (récit 36), around the birthday. */
    wishes: (@Composable (Member) -> Unit)?,
    scrollConnection: NestedScrollConnection? = null,
) {
    val member = state.member
    val data = v2.state
    val today = data?.today ?: java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris"))
    val viewingAs = LocalViewAs.current.viewed != null
    val mayFill = state.isMe && !viewingAs
    val mayEditCard = data?.canEditCard == true && !viewingAs
    val card = data?.card
    var editingCard by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state, today, onSavePhoto = v2.onSavePhoto?.takeIf { mayFill }, onRemovePhoto = v2.onRemovePhoto)
        card?.let { NowCard(it, editable = mayFill && mayEditCard, onSave = { text -> v2.onSaveCard(it.copy(now = text)) }) }
        member.phone?.takeIf { hasCoordinates(member) && !state.isMe }?.let { ContactButtons(it) }
        if (mayFill && data != null) FillGauge(member, data, state.souvenirs.isNotEmpty())
        wishes?.invoke(member)
        val onEditCard = if (mayEditCard) ({ editingCard = true }) else null
        card?.let { KnowPart(it, state.isMe, onEditCard) }
        card?.let { TastesPart(it, state.isMe, onEditCard) }
        GiftsPart(member, state.isMe, today, data?.wishCount ?: 0, v2.onOpenWishes, v2.onPrepareSurprise?.takeIf { !state.isMe && !viewingAs })
        v2.onOpenRecipes?.let { open -> RecipesPart(state.isMe, data?.recipeCount ?: 0, open, v2.onAddRecipe?.takeIf { mayFill }) }
        data?.let { MomentsPart(it, state.isMe, v2.onOpenEvent) }
        SuccessesPart(state.souvenirs, state.isMe)
        data?.let { NewsPart(it) }
        data?.let { CoordinatesPart(member, it, state.isMe, mayFill, v2.onSavePhone, v2.onSaveAddress) }
        if (state.saveFailed || data?.saveFailed == true) {
            Text(
                stringResource(R.string.profile_v2_save_failed),
                style = onPhotoStyle(MaterialTheme.typography.bodyLarge),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (editingCard && card != null) {
        MemberCardDialog(card, kid = member.profile != MemberProfile.Adult, onSave = v2.onSaveCard, onDismiss = { editingCard = false })
    }
}

/**
 * The top of the page (F6 à F8): the round photo with its white ring, a camera on my own page, the
 * first name, « Mamie » in quotes, the age of a child and the birthday, and « Anniversaire dans 12
 * jours » in the 30 days before.
 */
@Composable
private fun Header(
    state: MemberProfileUiState.Ready,
    today: java.time.LocalDate,
    onSavePhoto: ((ByteArray) -> Unit)?,
    onRemovePhoto: () -> Unit,
) {
    val member = state.member
    val photo = LocalMemberPhotos.current[member.id]
    var fullPhoto by rememberSaveable { mutableStateOf(false) }
    var changePhoto by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            val photoLabel = stringResource(R.string.profile_v2_photo_open, member.firstName)
            Box(Modifier.border(4.dp, Color.White, CircleShape)) {
                MemberAvatar(
                    member,
                    size = AVATAR_SIZE,
                    modifier = if (photo != null) Modifier.clip(CircleShape).clickable(onClickLabel = photoLabel) { fullPhoto = true } else Modifier,
                )
            }
            if (onSavePhoto != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).size(56.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.profile_v2_photo_change)) { changePhoto = true },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.profile_v2_photo_change), tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
        Text(
            member.firstName,
            style = onPhotoStyle(MaterialTheme.typography.headlineLarge),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        member.nickname?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.profile_v2_nickname, it), style = onPhotoStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)))
        }
        member.birthDate?.let { birth ->
            val line = if (member.profile == MemberProfile.Adult) {
                stringResource(R.string.profile_v2_birthday_line, birthdayLabel(birth))
            } else {
                val age = ageOn(birth, today)
                stringResource(R.string.profile_v2_age_line, pluralStringResource(R.plurals.birthday_age, age, age), birthdayLabel(birth))
            }
            Text(line, style = onPhotoStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)), textAlign = TextAlign.Center)
            if (birthdaySoon(birth, today)) BirthdayPill(daysUntilBirthday(birth, today))
        }
    }
    // The same full screen as the Famille tab, with its zoom (retour d'essai, 20/11).
    if (fullPhoto && photo != null) {
        MemberPhotoViewer(
            member = member,
            photo = photo,
            onDismiss = { fullPhoto = false },
            onChange = if (onSavePhoto != null) ({ fullPhoto = false; changePhoto = true }) else null,
        )
    }
    if (changePhoto && onSavePhoto != null) {
        MemberPhotoEditor(member = member, hasPhoto = photo != null, onSave = onSavePhoto, onRemove = onRemovePhoto, onDismiss = { changePhoto = false })
    }
}

private val AVATAR_SIZE = 112.dp

/** « 🎂 Anniversaire dans 12 jours », a yellow pill (F7). */
@Composable
private fun BirthdayPill(days: Int) {
    val text = if (days == 0) {
        stringResource(R.string.profile_v2_birthday_pill_today)
    } else {
        stringResource(R.string.profile_v2_birthday_pill, pluralStringResource(R.plurals.gifts_in_days, days, days))
    }
    Surface(shape = RoundedCornerShape(16.dp), color = PillYellow, contentColor = Color(0xFF3A2A1A)) {
        Text("🎂 $text", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

private val PillYellow = Color(0xFFFFE7A3)

/**
 * « En ce moment » (récit 114): one free line under the name. On my own page, « Changer » stays on
 * the page, outside « Modifier » (règle 6): it changes more often. Hidden on someone else's page
 * when they wrote nothing.
 */
@Composable
private fun NowCard(card: MemberCard, editable: Boolean, onSave: (String) -> Boolean) {
    if (card.now.isBlank() && !editable) return
    var changing by rememberSaveable { mutableStateOf(false) }
    FicheCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.profile_v2_now).uppercase(), style = MaterialTheme.typography.labelLarge, color = ficheInkSoft())
            if (card.now.isNotBlank()) Text(card.now, style = MaterialTheme.typography.titleMedium)
            if (editable) {
                TextButton(onClick = { changing = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(
                        stringResource(if (card.now.isBlank()) R.string.profile_v2_now_write else R.string.profile_v2_now_change),
                        color = ficheInk(),
                        textDecoration = TextDecoration.Underline,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
    if (changing) {
        TextDialog(
            title = stringResource(R.string.profile_v2_now),
            initial = card.now,
            max = MemberCard.NOW_MAX,
            hint = stringResource(R.string.profile_v2_now_hint),
            onSave = { if (onSave(it)) changing = false },
            onDismiss = { changing = false },
        )
    }
}

/** Appeler and Message (F7), side by side, for an adult who gave a phone, never on my own page. */
@Composable
private fun ContactButtons(phone: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassButton(Icons.Outlined.Phone, stringResource(R.string.profile_v2_call), Modifier.weight(1f)) { dial(context, phone) }
        GlassButton(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.profile_v2_message), Modifier.weight(1f)) { message(context, phone) }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides ficheInk()) {
        Row(
            modifier
                .heightIn(min = 56.dp)
                .ficheCard(28.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun dial(context: android.content.Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { c -> c.isDigit() || c == '+' }))) }
}

private fun message(context: android.content.Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number.filter { c -> c.isDigit() || c == '+' }))) }
}

private fun directions(context: android.content.Context, place: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))) }
}

/** « Ta fiche est remplie à 80 % » (PRODUIT-116), with what is missing. Gone once everything is there. */
@Composable
private fun FillGauge(member: Member, data: ProfileV2State, hasSouvenir: Boolean) {
    val parts = fillParts(member, LocalMemberPhotos.current[member.id] != null, data, hasSouvenir)
    val percent = fillPercent(parts)
    if (percent == 100) return
    val missing = parts.filter { !it.second }.map { fillPartLabel(it.first) }
    FicheCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.profile_v2_fill_title, percent), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { percent / 100f },
                color = ficheInk(),
                trackColor = ficheInk().copy(alpha = 0.15f),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Text(stringResource(R.string.profile_v2_fill_missing, joinNames(missing)), style = MaterialTheme.typography.bodyMedium, color = ficheInkSoft())
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
 * « À savoir » (DESIGN-165): allergies, diet and what not to offer, seen by the whole family, under
 * the name. A blank field says « Non précisé », never « Aucune »: nobody should read « no allergy »
 * where nothing was written. Hidden on someone else's page while all three are blank.
 */
@Composable
private fun KnowPart(card: MemberCard, isMe: Boolean, onEdit: (() -> Unit)?) {
    val blank = card.allergies.isBlank() && card.diet.isBlank() && card.avoid.isBlank()
    if (blank && !isMe) return
    val unknown = stringResource(R.string.profile_v2_unknown)
    FicheSection(stringResource(R.string.profile_v2_know), onEdit = onEdit, editLabel = stringResource(R.string.profile_v2_know_edit)) {
        FicheRows(
            listOf<@Composable () -> Unit>(
                { FicheRow("⚠️", stringResource(R.string.profile_v2_allergies), card.allergies.ifBlank { unknown }) },
                { FicheRow("🥗", stringResource(R.string.profile_v2_diet), card.diet.ifBlank { unknown }) },
                { FicheRow("🚫", stringResource(if (isMe) R.string.profile_v2_avoid_mine else R.string.profile_v2_avoid), card.avoid.ifBlank { unknown }) },
            ),
        )
        if (isMe) FicheSmall(stringResource(R.string.profile_v2_know_seen))
    }
}

/**
 * « Goûts et passions » (DESIGN-166): the shoe size, the sizes of top and bottom, the ring size, the
 * tastes and the passions, each on its row when written. Empty, it shows on my page only, as an
 * invitation.
 */
@Composable
private fun TastesPart(card: MemberCard, isMe: Boolean, onEdit: (() -> Unit)?) {
    val rows = buildList<@Composable () -> Unit> {
        if (card.shoes.isNotBlank()) add { FicheRow("👟", stringResource(R.string.member_card_shoes), card.shoes) }
        if (card.top.isNotBlank()) add { FicheRow("👕", stringResource(R.string.profile_v2_size_top), card.top) }
        if (card.bottom.isNotBlank()) add { FicheRow("👖", stringResource(R.string.profile_v2_size_bottom), card.bottom) }
        if (card.ring.isNotBlank()) add { FicheRow("💍", stringResource(R.string.profile_v2_size_ring), card.ring) }
        if (card.tastes.isNotBlank()) add { FicheRow("❤️", stringResource(R.string.member_card_tastes), card.tastes) }
        if (card.hobbies.isNotBlank()) add { FicheRow("✨", stringResource(R.string.profile_v2_passions), card.hobbies) }
    }
    if (rows.isEmpty() && !(isMe && onEdit != null)) return
    FicheSection(
        stringResource(if (isMe) R.string.member_card_section_mine else R.string.member_card_section),
        onEdit = onEdit,
        editLabel = stringResource(R.string.profile_v2_tastes_edit),
    ) {
        if (rows.isEmpty()) {
            FicheRow("❤️", stringResource(R.string.member_card_fill_mine), stringResource(R.string.member_card_invite), onClick = onEdit)
        } else {
            FicheRows(rows)
        }
    }
}

/**
 * « Cadeaux » (F7, F9): their wishes, then « Préparer une surprise » (never on my page). In the 30
 * days before someone else's birthday, a dark row « Son anniversaire est dans 9 jours ». Only the
 * number of wishes: never « Réservé » nor « Pris », above all on my own page.
 */
@Composable
private fun GiftsPart(
    member: Member,
    isMe: Boolean,
    today: java.time.LocalDate,
    wishCount: Int,
    onOpenWishes: (() -> Unit)?,
    onPrepareSurprise: (() -> Unit)?,
) {
    val open = onOpenWishes ?: return
    val days = member.birthDate?.takeIf { !isMe && birthdaySoon(it, today) }?.let { daysUntilBirthday(it, today) }
    FicheSection(stringResource(R.string.profile_v2_gifts)) {
        days?.let { d ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(
                    "🎂 " + if (d == 0) {
                        stringResource(R.string.profile_v2_birthday_band_today)
                    } else {
                        stringResource(R.string.profile_v2_birthday_band, pluralStringResource(R.plurals.gifts_in_days, d, d))
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        val rows = buildList<@Composable () -> Unit> {
            add {
                FicheRow(
                    "🎁",
                    stringResource(if (isMe) R.string.profile_v2_my_wishes_count else R.string.profile_v2_wishes_count, wishCount),
                    stringResource(
                        when {
                            wishCount > 0 -> R.string.profile_v2_wishes_open
                            isMe -> R.string.profile_v2_wishes_first
                            else -> R.string.profile_v2_wishes_none
                        },
                    ),
                    onClick = open,
                )
            }
            onPrepareSurprise?.let { prepare ->
                add { FicheRow("🎉", stringResource(R.string.profile_v2_surprise), stringResource(R.string.profile_v2_surprise_hidden, member.firstName), onClick = prepare) }
            }
        }
        FicheRows(rows)
    }
}

/** « Recettes » (règle 19): the recipes this member wrote, and « Ajouter une recette » on my own page. */
@Composable
private fun RecipesPart(isMe: Boolean, count: Int, onOpen: () -> Unit, onAdd: (() -> Unit)?) {
    FicheSection(stringResource(if (isMe) R.string.profile_v2_recipes_mine else R.string.profile_v2_recipes)) {
        val rows = buildList<@Composable () -> Unit> {
            add {
                FicheRow(
                    "🍲",
                    stringResource(if (isMe) R.string.profile_recipes_mine else R.string.profile_recipes),
                    if (count == 0) stringResource(R.string.profile_v2_recipes_none) else pluralStringResource(R.plurals.profile_v2_recipes_count, count, count),
                    onClick = onOpen,
                )
            }
            onAdd?.let { add { FicheRow("➕", stringResource(R.string.profile_v2_recipes_add), stringResource(R.string.profile_v2_recipes_add_hint), onClick = it) } }
        }
        FicheRows(rows)
    }
}

/** « Ses moments »: the next events they come to (« Je viens »), three at most. */
@Composable
private fun MomentsPart(data: ProfileV2State, isMe: Boolean, onOpenEvent: (String) -> Unit) {
    if (data.moments.isEmpty() && !isMe) return
    FicheSection(stringResource(if (isMe) R.string.profile_v2_moments_mine else R.string.profile_v2_moments)) {
        if (data.moments.isEmpty()) {
            FicheSmall(stringResource(R.string.profile_v2_moments_empty))
        } else {
            FicheRows(
                data.moments.map<Event, @Composable () -> Unit> { event ->
                    {
                        val time = event.time?.let { " · " + formatTime(it) }.orEmpty()
                        FicheRow(
                            "📅",
                            event.title,
                            dayShortLabel(event.date, data.today) + time,
                            onClick = { onOpenEvent(event.id) },
                            onClickLabel = stringResource(R.string.profile_v2_open_event),
                        )
                    }
                },
            )
        }
    }
}

/**
 * « Réussites » (ex-souvenirs, règle 8): public for the whole family, with no switch any more. The
 * date it was earned shows too, the first « bien arrivé » included (lot A3).
 */
@Composable
private fun SuccessesPart(souvenirs: List<Souvenir>, isMe: Boolean) {
    val earned = souvenirs.mapNotNull { s -> SouvenirKind.of(s.key)?.let { it to s } }
    if (earned.isEmpty()) return
    FicheSection(stringResource(if (isMe) R.string.profile_v2_successes_mine else R.string.profile_v2_successes)) {
        FicheRows(
            earned.map<Pair<SouvenirKind, Souvenir>, @Composable () -> Unit> { (kind, souvenir) ->
                { FicheRow("🏅", stringResource(kind.title), souvenir.earnedAt?.let { stringResource(R.string.profile_v2_success_on, frenchDate(it, "d MMMM yyyy")) }) }
            },
        )
        if (isMe) FicheSmall(stringResource(R.string.profile_v2_successes_seen))
    }
}

/** « Actualités »: the last photos of Bonnes nouvelles about them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsPart(data: ProfileV2State) {
    if (data.photos.isEmpty()) return
    FicheSection(stringResource(R.string.profile_v2_good_news)) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.photos.forEach { news ->
                StorageImage(
                    news.thumbnailPath(news.photoIds.first()),
                    stringResource(R.string.profile_v2_photo_news),
                    Modifier.width(96.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}

/**
 * « Coordonnées » (PRODUIT-98): a phone per adult, the address of the household; a child has none
 * (« Pour joindre Noé, passe par ses parents : Inès et Hugo », with « Appeler » for each parent who
 * gave a phone).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoordinatesPart(
    member: Member,
    data: ProfileV2State,
    isMe: Boolean,
    mayFill: Boolean,
    onSavePhone: (String) -> Unit,
    onSaveAddress: (String) -> Unit,
) {
    val context = LocalContext.current
    if (!hasCoordinates(member)) {
        val parents = data.kinship.parents
        if (parents.isEmpty()) return
        FicheSection(stringResource(R.string.profile_v2_coordinates)) {
            FicheSmall(stringResource(R.string.profile_v2_reach_parents_named, member.firstName, joinNames(parents.map { it.name })))
            val phones = parents.mapNotNull { p -> p.member?.takeIf { hasCoordinates(it) }?.phone?.let { p.name to it } }
            if (phones.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    phones.forEach { (name, phone) ->
                        FilledTonalButton(onClick = { dial(context, phone) }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.profile_v2_call_name, name))
                        }
                    }
                }
            }
        }
        return
    }
    val phone = member.phone
    val address = data.household?.address
    val mayAddress = mayFill && data.managesHousehold
    if (phone == null && address == null && !mayFill) return
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    FicheSection(stringResource(if (isMe) R.string.profile_v2_coordinates_mine else R.string.profile_v2_coordinates)) {
        val rows = buildList<@Composable () -> Unit> {
            if (phone != null || mayFill) {
                add {
                    FicheRow(
                        "📞",
                        stringResource(if (isMe) R.string.profile_v2_phone_title else R.string.profile_v2_phone),
                        phone ?: stringResource(R.string.profile_v2_phone_add),
                        onClick = if (mayFill) ({ editing = "phone" }) else null,
                        onClickLabel = stringResource(if (phone == null) R.string.profile_v2_phone_add else R.string.profile_v2_phone_edit),
                        trailing = if (phone != null && !isMe) ({ RoundAction(Icons.Outlined.Phone, stringResource(R.string.profile_v2_call)) { dial(context, phone) } }) else null,
                    )
                }
            }
            if (address != null || mayAddress) {
                add {
                    FicheRow(
                        "🏠",
                        stringResource(R.string.profile_v2_address_of),
                        address ?: stringResource(R.string.profile_v2_address_add),
                        onClick = if (mayAddress) ({ editing = "address" }) else null,
                        onClickLabel = stringResource(if (address == null) R.string.profile_v2_address_add else R.string.profile_v2_address_edit),
                        trailing = if (address != null && !isMe) ({ RoundAction(Icons.Outlined.Directions, stringResource(R.string.profile_v2_directions)) { directions(context, address) } }) else null,
                    )
                }
            }
        }
        FicheRows(rows)
        if (isMe) FicheSmall(stringResource(R.string.profile_v2_coordinates_help))
    }
    when (editing) {
        "phone" -> TextDialog(
            title = stringResource(R.string.profile_v2_phone_title),
            initial = phone.orEmpty(),
            max = 24,
            keyboard = KeyboardType.Phone,
            valid = { it.isBlank() || isPhone(it.trim()) },
            hint = stringResource(R.string.profile_v2_coordinates_empty_removes),
            onSave = { onSavePhone(it); editing = null },
            onDismiss = { editing = null },
        )
        "address" -> TextDialog(
            title = stringResource(R.string.profile_v2_address_title),
            initial = address.orEmpty(),
            max = 200,
            hint = stringResource(R.string.profile_v2_coordinates_empty_removes),
            onSave = { onSaveAddress(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** A round button of 56 dp at the end of a row: « Appeler », « Itinéraire ». */
@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = label)
    }
}

/** A short text in a dialog: « En ce moment », the phone, the address. */
@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    max: Int,
    hint: String,
    keyboard: KeyboardType = KeyboardType.Text,
    valid: (String) -> Boolean = { true },
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(max) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = KeyboardCapitalization.Sentences),
                isError = !valid(text),
                supportingText = { Text(hint) },
            )
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

/** « A », « A et B », « A, B et C ». */
@Composable
internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> stringResource(R.string.profile_v2_and, names.dropLast(1).joinToString(", "), names.last())
}

/** « 7 décembre ». */
internal fun birthdayLabel(date: java.time.LocalDate): String = frenchDate(date, "d MMMM")
