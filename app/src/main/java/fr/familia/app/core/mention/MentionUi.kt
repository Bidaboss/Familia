package fr.familia.app.core.mention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.PickGroup

/** The style of a « @Prénom »: bold, in the main color. */
@Composable
fun mentionStyle(): SpanStyle = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

/** Bolds the « @Prénom » of [chosen] while typing; the text itself does not change. */
private class MentionTransformation(private val chosen: List<Member>, private val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val ranges = mentionRanges(text.text, chosen)
        if (ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            ranges.forEach { (range, _) -> addStyle(style, range.first, range.last + 1) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * A text field where « @ » suggests the members of [groups], by household (Kévin, 19/11). [chosen]
 * are the members named so far; [onChange] gives the new text and the members still named in it.
 */
@Composable
fun MentionTextField(
    value: TextFieldValue,
    onChange: (TextFieldValue, List<Member>) -> Unit,
    groups: List<PickGroup>,
    chosen: List<Member>,
    label: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
) {
    val style = mentionStyle()
    val transformation = remember(chosen, style) { MentionTransformation(chosen, style) }
    val query = activeMentionQuery(value.text, value.selection.start).takeIf { value.selection.collapsed && enabled }
    fun still(text: String, list: List<Member>) = list.filter { it.id in mentionedIds(text, list) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                val next = backspaceMention(value, typed, chosen)
                if (next.text.length <= maxLength) onChange(next, still(next.text, chosen))
            },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            isError = isError,
            supportingText = supportingText,
            minLines = minLines,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (query != null) {
            val shown = groups.map { g -> g.copy(members = mentionMatches(query.query, g.members)) }.filter { it.members.isNotEmpty() }
            if (shown.isNotEmpty()) {
                MentionSuggestions(shown) { member ->
                    val next = insertMention(value, query, member)
                    val list = (chosen + member).distinctBy { it.id }
                    if (next.text.length <= maxLength) onChange(next, still(next.text, list))
                }
            }
        }
    }
}

/**
 * The same field for a plain [text]: the cursor is kept inside. [mentionIds] are the members named
 * so far, found in the family; [onChange] gives the new text and the ids still named in it.
 */
@Composable
fun MentionTextField(
    text: String,
    mentionIds: List<String>,
    onChange: (String, List<String>) -> Unit,
    groups: List<PickGroup>,
    label: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val value = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))
    MentionTextField(
        value = value,
        onChange = { next, named ->
            field = next
            onChange(next.text, named.map { it.id })
        },
        groups = groups,
        chosen = mentionMembers(mentionIds),
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        minLines = minLines,
        maxLength = maxLength,
    )
}

/** The members to name, by household: photo and first name, 56 dp rows. */
@Composable
private fun MentionSuggestions(groups: List<PickGroup>, onPick: (Member) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            groups.forEach { group ->
                if (group.name.isNotBlank() && groups.size > 1) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
                    )
                }
                group.members.forEach { member ->
                    val label = stringResource(R.string.mention_pick, member.firstName)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClickLabel = label, role = Role.Button) { onPick(member) }
                            .padding(horizontal = 16.dp),
                    ) {
                        MemberAvatar(member, size = 32.dp)
                        Text(member.firstName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/**
 * [text] with each « @Prénom » of [named] in bold; touching one opens that member ([onOpenMember]),
 * unless it is null (« Voir comme », previews).
 */
@Composable
fun MentionText(
    text: String,
    named: List<Member>,
    onOpenMember: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val span = mentionStyle()
    val annotated = remember(text, named, span, onOpenMember) { mentionAnnotated(text, named, span, onOpenMember) }
    Text(
        annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout,
    )
}

/** [text] with the « @Prénom » of the members [mentionIds], found in the family; a touch opens them. */
@Composable
fun MentionText(
    text: String,
    mentionIds: Collection<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    MentionText(text, mentionMembers(mentionIds), openMemberFromMention(), modifier, style, color, maxLines, onTextLayout)
}

internal fun mentionAnnotated(text: String, named: List<Member>, span: SpanStyle, onOpenMember: ((String) -> Unit)?): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        mentionRanges(text, named).forEach { (range, member) ->
            append(text.substring(at, range.first))
            val name = text.substring(range.first, range.last + 1)
            if (onOpenMember == null) {
                withStyle(span) { append(name) }
            } else {
                withLink(LinkAnnotation.Clickable("member:${member.id}", TextLinkStyles(span)) { onOpenMember(member.id) }) { append(name) }
            }
            at = range.last + 1
        }
        append(text.substring(at))
    }
