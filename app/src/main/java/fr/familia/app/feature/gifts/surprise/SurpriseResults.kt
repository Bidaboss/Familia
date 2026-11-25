package fr.familia.app.feature.gifts.surprise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.form.FormHint
import java.text.NumberFormat
import java.util.Locale

private val RankBack = Color(0xFFF3EADB)
private val WishPillBack = Color(0xFFFBE3EE)
private val WishPillText = Color(0xFF9B2F63)
private val IdeaPillBack = Color(0xFFE3EEFB)
private val IdeaPillText = Color(0xFF2F4F7A)
private val AverageText = Color(0xFFB5651D)
private val BestBack = Color(0xFFFFF1C9)
private val BestText = Color(0xFF7A4B00)
private val BarBack = Color(0xFFE4D9C4)
private val ChipBack = Color(0xFFF1E8DA)
private val DoneBack = Color(0xFFE4F0DA)
private val DoneText = Color(0xFF2F6B4A)
private val HintBack = Color(0xFFFFF3C4)
private val HintText = Color(0xFF5A3A12)
private val QuietText = Color(0xFF8A7B68)

/**
 * « Résultats » (brief §4, fiches C5 and C6): the proposals ranked by average, with ✕ to take one out
 * of the list, « Je le prends » / « Cadeau commun » on each one nobody took, then « Valider ma
 * participation » and where each member stands.
 */
@Composable
internal fun Results(state: SurpriseUiState.Ready, actions: SurpriseActions, tabs: @Composable () -> Unit) {
    val name = state.beneficiary.firstName
    var removedOpen by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var declining by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "notice") { SurpriseNotice("🎉 " + stringResource(R.string.surprise_never_sees, name)) }
        item(key = "tabs") { tabs() }
        if (state.failed) {
            item(key = "failed") {
                FormHint(stringResource(R.string.surprise_write_failed), isError = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        if (state.isReady) item(key = "ready") { ReadyBanner() }
        if (state.declined) item(key = "declined") { SurpriseNotice(stringResource(R.string.surprise_declined_notice)) }
        item(key = "intro") { Hint(stringResource(R.string.surprise_results_intro, name)) }
        if (state.results.isEmpty()) item(key = "none") { Hint(stringResource(R.string.surprise_results_none)) }
        items(state.results, key = { "result-${it.proposal.id}" }) { row ->
            ResultTile(state, row, actions, onRemove = { removing = row.proposal.id })
        }
        if (state.removed.isNotEmpty()) {
            item(key = "removed") {
                Foldable(stringResource(R.string.surprise_removed_title, state.removed.size), removedOpen) { removedOpen = !removedOpen }
            }
            if (removedOpen) {
                items(state.removed, key = { "removed-${it.proposal.id}" }) { row -> RemovedRow(row, enabled = !state.declined) { actions.onRemove(row, false) } }
            }
        }
        if (!state.declined) item(key = "validate") { ValidationCard(state, actions, onDecline = { declining = true }) }
    }
    val toRemove = state.results.firstOrNull { it.proposal.id == removing }
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.surprise_remove_title, toRemove.proposal.title)) },
            text = {
                Text(
                    stringResource(
                        if (toRemove.proposal.kind == ProposalKind.Wish) R.string.surprise_remove_wish_body else R.string.surprise_remove_idea_body,
                        name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { actions.onRemove(toRemove, true); removing = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.surprise_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (declining) {
        AlertDialog(
            onDismissRequest = { declining = false },
            title = { Text(stringResource(R.string.surprise_decline_title)) },
            text = { Text(stringResource(R.string.surprise_decline_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDecline(); declining = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.surprise_decline_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { declining = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ReadyBanner() {
    Surface(color = DoneBack, contentColor = DoneText, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "✓ " + stringResource(R.string.surprise_ready),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** One proposal of the list: rank, title, price, « ♥ Envie de X » or « 💡 Idée de Y », its average, ✕, and what is taken. */
@Composable
private fun ResultTile(state: SurpriseUiState.Ready, row: ResultRow, actions: SurpriseActions, onRemove: () -> Unit) {
    val p = row.proposal
    val wish = p.kind == ProposalKind.Wish
    Surface(
        color = if (wish) WishPink else Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(20.dp),
        border = if (wish) BorderStroke(1.dp, WishEdge) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = RankBack, modifier = Modifier.padding(top = 4.dp).size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(row.rank.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    p.price?.let { Text(euros(it), style = MaterialTheme.typography.bodyMedium) }
                    Pill(
                        text = when {
                            wish -> "♥ " + stringResource(R.string.surprise_wish_of, state.beneficiary.firstName)
                            row.isMine -> "💡 " + stringResource(R.string.surprise_my_idea)
                            else -> "💡 " + stringResource(R.string.surprise_idea_of, p.authorName)
                        },
                        back = if (wish) WishPillBack else IdeaPillBack,
                        text2 = if (wish) WishPillText else IdeaPillText,
                    )
                }
                // Taken, it stays in the list: its owner lets it go first (the rules refuse ✕).
                if (row.take == null && !state.declined) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.surprise_remove_action, p.title))
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(averageLine(p.average, p.ratingCount, state.surprise.memberIds.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = AverageText)
                when {
                    row.unanimous -> Pill("🤝 " + stringResource(R.string.surprise_unanimous), BestBack, BestText)
                    row.best -> Pill("🏆 " + stringResource(R.string.surprise_best), BestBack, BestText)
                }
            }
            val take = row.take
            when {
                take == null -> if (!state.declined) TakeButtons(state, row, actions)
                take.kind == TakeKind.Solo -> SoloTaken(row, take, enabled = !state.declined, onRelease = { actions.onRelease(row) })
                else -> JointGift(state, row, take, actions)
            }
        }
    }
}

@Composable
private fun averageLine(average: Double?, count: Int, members: Int): String = when {
    count == 0 -> stringResource(R.string.surprise_no_rating)
    average == null -> pluralStringResource(R.plurals.surprise_few_ratings, count, count)
    else -> stringResource(
        R.string.surprise_average,
        NumberFormat.getNumberInstance(Locale.FRANCE).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(average),
        count,
        members,
    )
}

@Composable
private fun Pill(text: String, back: Color, text2: Color) {
    Surface(shape = RoundedCornerShape(50), color = back, contentColor = text2) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

/** « 🎁 Je le prends » and, for an adult, « 👪 Cadeau commun ». */
@Composable
private fun TakeButtons(state: SurpriseUiState.Ready, row: ResultRow, actions: SurpriseActions) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { actions.onTakeAlone(row) },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) { Text("🎁 " + stringResource(R.string.surprise_take_alone), fontWeight = FontWeight.ExtraBold, color = Brown, textAlign = TextAlign.Center) }
        if (state.isAdult) {
            OutlinedButton(
                onClick = { actions.onTakeTogether(row) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text("👪 " + stringResource(R.string.surprise_take_together), fontWeight = FontWeight.ExtraBold, color = Brown, textAlign = TextAlign.Center) }
        }
    }
}

/** « 🎁 Je m'en occupe » / « 🎁 X s'en occupe ». */
@Composable
private fun SoloTaken(row: ResultRow, take: SurpriseTake, enabled: Boolean, onRelease: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "🎁 " + if (row.takenByMe) stringResource(R.string.surprise_taken_by_me) else stringResource(R.string.surprise_taken_by, row.ownerName.orEmpty()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        if (row.takenByMe && enabled) {
            TextButton(onClick = onRelease, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.surprise_release), color = QuietText, textDecoration = TextDecoration.Underline)
            }
        }
        take.boughtBy?.let { Text(stringResource(R.string.surprise_bought), style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * The joint gift (fiche C5): a bar of what is gathered on the goal, the parts (named for its owner
 * only, the others read the total), and « Ma part » with − and +.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JointGift(state: SurpriseUiState.Ready, row: ResultRow, take: SurpriseTake, actions: SurpriseActions) {
    val goal = take.goal ?: 0
    Box(Modifier.fillMaxWidth().height(1.dp).background(WishEdge))
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("👪 " + stringResource(R.string.surprise_joint_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (!row.takenByMe) {
            Text(stringResource(R.string.surprise_joint_by, row.ownerName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        }
        val part = if (goal > 0) (take.sharesTotal.toFloat() / goal).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(BarBack)) {
            Box(Modifier.fillMaxWidth(part).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Accent))
        }
        Text(
            if (take.missing > 0) {
                stringResource(R.string.surprise_joint_gathered_missing, take.sharesTotal, goal, take.missing)
            } else {
                stringResource(R.string.surprise_joint_gathered, take.sharesTotal, goal)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val chips = if (row.takenByMe) row.shares else listOfNotNull(row.myShare?.let { "" to it })
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val me = stringResource(R.string.surprise_me)
                chips.forEach { (who, amount) ->
                    Surface(shape = RoundedCornerShape(50), color = ChipBack) {
                        Text(
                            "${who.ifEmpty { me }} · $amount €",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        if (!state.declined) {
            if (state.isAdult) MyPart(row, actions)
            if (row.takenByMe) {
                TextButton(onClick = { actions.onRelease(row) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.surprise_joint_close), color = QuietText, textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}

/** « Ma part » in whole euros: − field +, written when the field is left or confirmed. */
@Composable
private fun MyPart(row: ResultRow, actions: SurpriseActions) {
    val stored = row.myShare ?: 0
    var text by rememberSaveable(row.proposal.id) { mutableStateOf(stored.takeIf { it > 0 }?.toString().orEmpty()) }
    LaunchedEffect(stored) { text = stored.takeIf { it > 0 }?.toString().orEmpty() }
    val partLabel = stringResource(R.string.surprise_my_part)
    val commit = { value: Int -> actions.onShare(row, value.coerceIn(0, SurpriseTake.MAX_AMOUNT)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.surprise_my_part), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        FilledIconButton(
            onClick = { commit((stored - 1).coerceAtLeast(0)) },
            enabled = stored > 0,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brown, contentColor = Color.White),
            modifier = Modifier.size(56.dp).padding(6.dp),
        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.surprise_part_less)) }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(text.toIntOrNull() ?: 0) }),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            modifier = Modifier.width(84.dp).semantics { contentDescription = partLabel },
        )
        Text(" €", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        FilledIconButton(
            onClick = { commit(stored + 1) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brown, contentColor = Color.White),
            modifier = Modifier.size(56.dp).padding(6.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.surprise_part_more)) }
    }
    val typed = text.toIntOrNull() ?: 0
    if (typed != stored) {
        TextButton(onClick = { commit(typed) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.surprise_part_save, typed))
        }
    }
}

/** One taken out of the list, with « Remettre ». */
@Composable
private fun RemovedRow(row: ResultRow, enabled: Boolean, onPutBack: () -> Unit) {
    Surface(color = Color.White, contentColor = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.proposal.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            if (enabled) {
                TextButton(onClick = onPutBack, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.surprise_put_back)) }
            }
        }
    }
}

/** « Valider ma participation » (fiche C6): what it means, the reminders, who stands where, the button. */
@Composable
private fun ValidationCard(state: SurpriseUiState.Ready, actions: SurpriseActions, onDecline: () -> Unit) {
    Surface(color = Color.White, contentColor = Ink, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("✅ " + stringResource(R.string.surprise_validate_title))
            Text(stringResource(R.string.surprise_validate_body), style = MaterialTheme.typography.bodyMedium)
            Surface(color = HintBack, contentColor = HintText, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "🔔 " + stringResource(R.string.surprise_validate_reminders),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            state.statuses.forEach { MemberLine(it) }
            if (state.validated) {
                Text("✓ " + stringResource(R.string.surprise_validated), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = DoneText)
                OutlinedButton(onClick = { actions.onValidate(false) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.surprise_unvalidate), fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Button(
                    onClick = { actions.onValidate(true) },
                    enabled = state.participates,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brown, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.surprise_validate_action), fontWeight = FontWeight.ExtraBold) }
                if (!state.participates) Hint(stringResource(R.string.surprise_validate_first))
            }
            TextButton(onClick = onDecline, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.surprise_decline_action), color = QuietText, textDecoration = TextDecoration.Underline)
            }
        }
    }
}

@Composable
private fun MemberLine(status: MemberStatus) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (status.isMe) stringResource(R.string.surprise_me) else status.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        val (label, back, text) = when (status.state) {
            MemberState.Done -> Triple("✓ " + stringResource(R.string.surprise_member_done), DoneBack, DoneText)
            MemberState.Waiting -> Triple(stringResource(R.string.surprise_member_waiting), BestBack, BestText)
            MemberState.Declined -> Triple(stringResource(R.string.surprise_member_declined), ChipBack, QuietText)
        }
        Pill(label, back, text)
    }
}
