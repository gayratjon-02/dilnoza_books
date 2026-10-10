package dilnoza.books.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData

fun RichText.toAnnotatedString(darkTheme: Boolean = false): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    val styles = spans.map { AnnotatedString.Range(it.style.toSpanStyle(darkTheme), it.start, it.end) }
    return AnnotatedString(text, styles)
}

fun SpanStyleData.toSpanStyle(darkTheme: Boolean = false): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        color = color?.let { Color(TextPalette.resolve(it, darkTheme)) } ?: Color.Unspecified,
        fontSize = fontSizeSp?.sp ?: TextUnit.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = fontFamily?.let(::fontFamilyOf),
        background = background?.let { Color(TextPalette.resolve(it, darkTheme)) } ?: Color.Unspecified,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
    )
}

private fun fontFamilyOf(name: String): FontFamily? = when (name) {
    "serif" -> FontFamily.Serif
    "sans-serif" -> FontFamily.SansSerif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> null
}

/**
 * Draws [rich]'s styles, plus temporary [marks] such as search matches, over the field's plain text and
 * hides the first [hiddenPrefix] characters
 * (the block field's sentinel). The rest is never changed, so the caret cannot move. If the field is a
 * frame ahead of [rich] (the text no longer matches), the text is drawn plain for that frame instead
 * of with misplaced styles.
 */
class RichTextTransformation(
    private val rich: RichText,
    private val darkTheme: Boolean,
    private val hiddenPrefix: Int = 0,
    private val marks: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
) : VisualTransformation {
    private val styled = rich.toAnnotatedString(darkTheme).let {
        if (marks.isEmpty()) it else AnnotatedString(it.text, it.spanStyles + marks)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = hiddenPrefix.coerceAtMost(text.length)
        val visible = if (prefix == 0) text else text.subSequence(prefix, text.length)
        val shown = if (visible.text == rich.text) styled else visible
        if (prefix == 0) return TransformedText(shown, OffsetMapping.Identity)
        val length = shown.length
        return TransformedText(
            shown,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = (offset - prefix).coerceIn(0, length)
                override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, length) + prefix
            },
        )
    }

    override fun equals(other: Any?): Boolean = other is RichTextTransformation &&
        other.rich == rich && other.darkTheme == darkTheme && other.hiddenPrefix == hiddenPrefix && other.marks == marks

    override fun hashCode(): Int = ((31 * rich.hashCode() + darkTheme.hashCode()) * 31 + hiddenPrefix) * 31 + marks.hashCode()
}
