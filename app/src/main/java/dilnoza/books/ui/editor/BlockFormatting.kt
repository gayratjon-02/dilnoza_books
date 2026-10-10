package dilnoza.books.ui.editor

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.editor.richtext.StyleAttribute
import dilnoza.books.editor.richtext.clearFormatting
import dilnoza.books.editor.richtext.has
import dilnoza.books.editor.richtext.isApplied
import dilnoza.books.editor.richtext.setBackground
import dilnoza.books.editor.richtext.setColor
import dilnoza.books.editor.richtext.setFontSize
import dilnoza.books.editor.richtext.styleAt
import dilnoza.books.editor.richtext.toggle
import dilnoza.books.editor.richtext.typingStyleAt
import dilnoza.books.editor.richtext.with

/** A character-style change from the format bar. */
sealed interface FormatChange {
    data class Toggle(val attribute: StyleAttribute) : FormatChange
    data class TextColor(val argb: Int?) : FormatChange
    data class Highlight(val argb: Int?) : FormatChange
    data class FontSize(val sp: Int?) : FormatChange
    data object Clear : FormatChange

    fun applyTo(style: SpanStyleData): SpanStyleData = when (this) {
        is Toggle -> style.with(attribute, !style.has(attribute))
        is TextColor -> style.copy(color = argb)
        is Highlight -> style.copy(background = argb)
        is FontSize -> style.copy(fontSizeSp = sp)
        Clear -> SpanStyleData.Default
    }

    fun applyTo(text: RichText, start: Int, end: Int): RichText = when (this) {
        is Toggle -> text.toggle(start, end, attribute)
        is TextColor -> text.setColor(start, end, argb)
        is Highlight -> text.setBackground(start, end, argb)
        is FontSize -> text.setFontSize(start, end, sp)
        Clear -> text.clearFormatting(start, end)
    }
}

/** What the format bar shows as active; colours and size are those of the selection's first character. */
data class FormatState(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val color: Int?,
    val background: Int?,
    val fontSizeSp: Int?,
)

/**
 * The format bar's state for the block's selection. With a collapsed caret it shows the style the
 * next typed character will get: [pending] if the writer just chose one, otherwise the inherited one.
 */
fun EditorBlock.formatState(pending: SpanStyleData?): FormatState {
    val selection = value.selection
    if (selection.collapsed) {
        val style = pending ?: rich.typingStyleAt(selection.start)
        return FormatState(
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            strikethrough = style.strikethrough,
            color = style.color,
            background = style.background,
            fontSizeSp = style.fontSizeSp,
        )
    }
    val start = selection.min
    val end = selection.max
    val first = rich.styleAt(start)
    return FormatState(
        bold = rich.isApplied(start, end, StyleAttribute.BOLD),
        italic = rich.isApplied(start, end, StyleAttribute.ITALIC),
        underline = rich.isApplied(start, end, StyleAttribute.UNDERLINE),
        strikethrough = rich.isApplied(start, end, StyleAttribute.STRIKETHROUGH),
        color = first.color,
        background = first.background,
        fontSizeSp = first.fontSizeSp,
    )
}

/**
 * Applies [change] to the selected text and returns null, or, with a collapsed caret, leaves the
 * text alone and returns the style the next typed characters should get (like a word processor).
 */
fun EditorBlock.applyFormat(change: FormatChange, pending: SpanStyleData?): SpanStyleData? {
    val selection = value.selection
    if (selection.collapsed) return change.applyTo(pending ?: rich.typingStyleAt(selection.start))
    setRich(change.applyTo(rich, selection.min, selection.max), selection)
    return null
}
