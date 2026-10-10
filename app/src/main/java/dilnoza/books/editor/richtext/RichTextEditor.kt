package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan

object RichTextEditor {

    /**
     * Carries the styles of [old] over to [newText], treating the difference as one replacement
     * found by common prefix and suffix (never cutting a surrogate pair).
     *
     * Inserted text gets [typingStyle] when given. Otherwise a replacement takes the style of the
     * first replaced character, and a pure insertion that of the character before it (the first
     * character when inserting at the start).
     *
     * With repeated characters the edit position is ambiguous ("aa" -> "aaa"); passing the new
     * caret position as [cursor] places the edit so it ends there.
     */
    fun applyTextChange(
        old: RichText,
        newText: String,
        typingStyle: SpanStyleData? = null,
        cursor: Int? = null,
    ): RichText {
        val base = old.normalized()
        val oldText = base.text
        if (oldText == newText) return base

        val shared = minOf(oldText.length, newText.length)
        var prefix = oldText.commonPrefixWith(newText).length
        var suffix = oldText.commonSuffixWith(newText).length
        if (cursor != null) {
            suffix = minOf(suffix, newText.length - newText.snapBackward(cursor.coerceIn(0, newText.length)))
            prefix = minOf(prefix, shared - suffix)
        } else {
            suffix = minOf(suffix, shared - prefix)
        }
        if (prefix > 0 && oldText[prefix - 1].isHighSurrogate()) prefix--
        if (suffix > 0 && oldText[oldText.length - suffix].isLowSurrogate()) suffix--

        val removedEnd = oldText.length - suffix
        val insertedEnd = newText.length - suffix
        val delta = insertedEnd - removedEnd
        val insertedStyle = when {
            insertedEnd == prefix -> null
            typingStyle != null -> typingStyle
            removedEnd > prefix -> base.styleAt(prefix)
            else -> base.typingStyleAt(prefix)
        }

        val out = ArrayList<StyleSpan>(base.spans.size + 1)
        for (run in base.spans) {
            if (run.start < prefix) out += StyleSpan(run.start, minOf(run.end, prefix), run.style)
        }
        if (insertedStyle != null) out += StyleSpan(prefix, insertedEnd, insertedStyle)
        for (run in base.spans) {
            if (run.end > removedEnd) out += StyleSpan(maxOf(run.start, removedEnd) + delta, run.end + delta, run.style)
        }
        return RichText(newText, compact(out))
    }
}
