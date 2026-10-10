package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.SpanStyleData

enum class StyleAttribute { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH }

fun SpanStyleData.has(attribute: StyleAttribute): Boolean = when (attribute) {
    StyleAttribute.BOLD -> bold
    StyleAttribute.ITALIC -> italic
    StyleAttribute.UNDERLINE -> underline
    StyleAttribute.STRIKETHROUGH -> strikethrough
}

fun SpanStyleData.with(attribute: StyleAttribute, enabled: Boolean): SpanStyleData = when (attribute) {
    StyleAttribute.BOLD -> copy(bold = enabled)
    StyleAttribute.ITALIC -> copy(italic = enabled)
    StyleAttribute.UNDERLINE -> copy(underline = enabled)
    StyleAttribute.STRIKETHROUGH -> copy(strikethrough = enabled)
}

/** Layers [top] over this style: every attribute [top] sets wins, the rest are kept. */
fun SpanStyleData.overlay(top: SpanStyleData): SpanStyleData = SpanStyleData(
    bold = bold || top.bold,
    italic = italic || top.italic,
    underline = underline || top.underline,
    strikethrough = strikethrough || top.strikethrough,
    color = top.color ?: color,
    background = top.background ?: background,
    fontSizeSp = top.fontSizeSp ?: fontSizeSp,
    fontFamily = top.fontFamily ?: fontFamily,
)
