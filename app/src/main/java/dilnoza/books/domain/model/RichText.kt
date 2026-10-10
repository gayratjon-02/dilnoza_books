package dilnoza.books.domain.model

/**
 * Plain text plus character styles. The canonical form keeps [spans] sorted, non-overlapping,
 * non-empty, within `[0, text.length]`, free of default styles and with equal neighbours merged.
 */
data class RichText(val text: String, val spans: List<StyleSpan> = emptyList()) {
    companion object {
        val Empty = RichText("")
    }
}

/** Styles the characters in `[start, end)`. */
data class StyleSpan(val start: Int, val end: Int, val style: SpanStyleData)

/** [color] and [background] are ARGB. */
data class SpanStyleData(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: Int? = null,
    val background: Int? = null,
    val fontSizeSp: Int? = null,
    val fontFamily: String? = null,
) {
    val isDefault: Boolean get() = this == Default

    companion object {
        val Default = SpanStyleData()
    }
}
