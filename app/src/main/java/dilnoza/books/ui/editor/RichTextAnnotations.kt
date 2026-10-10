package dilnoza.books.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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

/** Vertical space inserted before the line that starts at [offset] (in the block's text), in points. */
data class PageGap(val offset: Int, val heightPt: Float)

/** Offsets of the [gaps] that are drawn in a text of [length]: inside it, each once, in order. */
private fun drawnGapOffsets(gaps: List<PageGap>, length: Int): List<Int> =
    gaps.map { it.offset }.filter { it in 1 until length }.distinct().sorted()

/**
 * Where character [offset] of a block's text of [length] sits in the text its field draws, which has
 * one extra character per page gap before it (see [RichTextTransformation]). Positions in the field's
 * text layout (caret rectangles, bounding boxes) must be looked up at this offset.
 */
fun drawnOffset(offset: Int, gaps: List<PageGap>, length: Int): Int {
    val r = offset.coerceIn(0, length)
    return r + drawnGapOffsets(gaps, length).count { it <= r }
}

/**
 * Draws [rich]'s styles, plus temporary [marks] such as search matches, over the field's plain text and
 * hides the first [hiddenPrefix] characters (the block field's sentinel). [gaps] push the lines that
 * continue on the next page below the page gap: each is a one-character paragraph as tall as the gap,
 * inserted only in the drawn text. The writer's text is never changed, so the caret cannot move. If the
 * field is a frame ahead of [rich] (the text no longer matches), the text is drawn plain for that frame.
 */
class RichTextTransformation(
    private val rich: RichText,
    private val darkTheme: Boolean,
    private val hiddenPrefix: Int = 0,
    private val marks: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    private val gaps: List<PageGap> = emptyList(),
) : VisualTransformation {
    private val styled = rich.toAnnotatedString(darkTheme).let {
        if (marks.isEmpty()) it else AnnotatedString(it.text, it.spanStyles + marks)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val prefix = hiddenPrefix.coerceAtMost(text.length)
        val visible = if (prefix == 0) text else text.subSequence(prefix, text.length)
        val matches = visible.text == rich.text
        val shown = if (matches) styled else visible
        val length = shown.length
        val offsets = if (matches) drawnGapOffsets(gaps, length) else emptyList()
        if (offsets.isEmpty()) {
            if (prefix == 0) return TransformedText(shown, OffsetMapping.Identity)
            return TransformedText(
                shown,
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = (offset - prefix).coerceIn(0, length)
                    override fun transformedToOriginal(offset: Int): Int = offset.coerceIn(0, length) + prefix
                },
            )
        }

        val heights = gaps.associate { it.offset to it.heightPt }
        val drawn = buildAnnotatedString {
            var from = 0
            offsets.forEach { offset ->
                append(shown.subSequence(from, offset))
                withStyle(ParagraphStyle(lineHeight = heights.getValue(offset).sp, lineHeightStyle = GapLineHeight)) {
                    append(GAP_CHAR)
                }
                from = offset
            }
            append(shown.subSequence(from, length))
        }
        // Gap characters sit at offset + index in the drawn text; a caret at a gap shows on the next page.
        return TransformedText(
            drawn,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = drawnOffset(offset - prefix, gaps, length)

                override fun transformedToOriginal(offset: Int): Int {
                    val t = offset.coerceIn(0, drawn.length)
                    val before = offsets.withIndex().count { (index, gap) -> gap + index < t }
                    return (t - before).coerceIn(0, length) + prefix
                }
            },
        )
    }

    override fun equals(other: Any?): Boolean = other is RichTextTransformation &&
        other.rich == rich && other.darkTheme == darkTheme && other.hiddenPrefix == hiddenPrefix &&
        other.marks == marks && other.gaps == gaps

    override fun hashCode(): Int =
        (((31 * rich.hashCode() + darkTheme.hashCode()) * 31 + hiddenPrefix) * 31 + marks.hashCode()) * 31 + gaps.hashCode()

    private companion object {
        const val GAP_CHAR = "\u200B"
        val GapLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    }
}
