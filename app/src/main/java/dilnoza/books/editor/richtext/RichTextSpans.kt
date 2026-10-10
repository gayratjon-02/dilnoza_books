package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan

/**
 * Returns the canonical form. Spans are clamped to the text and widened so they never cut a
 * surrogate pair. Where spans overlap, the later one in the list wins for every attribute it
 * sets ([overlay]); attributes it leaves at default fall through to earlier spans.
 */
fun RichText.normalized(): RichText {
    val length = text.length
    val clamped = ArrayList<StyleSpan>(spans.size)
    for (span in spans) {
        if (span.style.isDefault) continue
        val start = text.snapBackward(span.start.coerceIn(0, length))
        val end = text.snapForward(span.end.coerceIn(0, length))
        if (start >= end) continue
        clamped += if (start == span.start && end == span.end) span else StyleSpan(start, end, span.style)
    }
    val result = compact(if (clamped.isSortedDisjoint()) clamped else clamped.flatten())
    return if (result == spans) this else RichText(text, result)
}

/** Style of the character at [index]; the default style outside the text or between spans. */
fun RichText.styleAt(index: Int): SpanStyleData {
    if (index < 0 || index >= text.length) return SpanStyleData.Default
    val runs = normalized().spans
    var low = 0
    var high = runs.lastIndex
    while (low <= high) {
        val mid = (low + high) ushr 1
        val run = runs[mid]
        when {
            index < run.start -> high = mid - 1
            index >= run.end -> low = mid + 1
            else -> return run.style
        }
    }
    return SpanStyleData.Default
}

/**
 * Style a character typed at caret [index] inherits: that of the character before it, or of the
 * first character when the caret is at the start.
 */
fun RichText.typingStyleAt(index: Int): SpanStyleData {
    val caret = index.coerceIn(0, text.length)
    return if (caret > 0) styleAt(caret - 1) else styleAt(0)
}

/** True when every character in `[start, end)` has [attribute]; an empty range asks [typingStyleAt]. */
fun RichText.isApplied(start: Int, end: Int, attribute: StyleAttribute): Boolean {
    val base = normalized()
    val (from, to) = base.text.styleRange(start, end)
    if (from >= to) return base.typingStyleAt(start).has(attribute)
    var covered = from
    for (run in base.spans) {
        if (run.end <= covered) continue
        if (run.start > covered || !run.style.has(attribute)) return false
        covered = run.end
        if (covered >= to) return true
    }
    return false
}

internal fun String.snapBackward(index: Int): Int =
    if (index in 1 until length && this[index].isLowSurrogate() && this[index - 1].isHighSurrogate()) index - 1 else index

internal fun String.snapForward(index: Int): Int =
    if (index in 1 until length && this[index].isLowSurrogate() && this[index - 1].isHighSurrogate()) index + 1 else index

/** Clamps a selection and widens it so a partly selected emoji is styled as a whole. */
internal fun String.styleRange(start: Int, end: Int): Pair<Int, Int> {
    val from = minOf(start, end).coerceIn(0, length)
    val to = maxOf(start, end).coerceIn(0, length)
    return snapBackward(from) to snapForward(to)
}

/** Expects sorted, disjoint spans; drops empty and default ones and merges equal neighbours. */
internal fun compact(spans: List<StyleSpan>): List<StyleSpan> {
    val out = ArrayList<StyleSpan>(spans.size)
    for (span in spans) {
        if (span.start >= span.end || span.style.isDefault) continue
        val last = out.lastOrNull()
        if (last != null && last.end == span.start && last.style == span.style) {
            out[out.lastIndex] = StyleSpan(last.start, span.end, span.style)
        } else {
            out += span
        }
    }
    return out
}

private fun List<StyleSpan>.isSortedDisjoint(): Boolean {
    for (i in 1 until size) if (this[i].start < this[i - 1].end) return false
    return true
}

private fun List<StyleSpan>.flatten(): List<StyleSpan> {
    val bounds = flatMap { listOf(it.start, it.end) }.distinct().sorted()
    val out = ArrayList<StyleSpan>()
    for (i in 0 until bounds.lastIndex) {
        val from = bounds[i]
        val to = bounds[i + 1]
        var style = SpanStyleData.Default
        for (span in this) if (span.start <= from && span.end >= to) style = style.overlay(span.style)
        out += StyleSpan(from, to, style)
    }
    return out
}
