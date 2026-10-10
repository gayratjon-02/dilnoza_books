package dilnoza.books.editor.search

/**
 * @property matchCase when false, letters are compared by locale-independent simple case folding
 *   (upper then lower per code point), so Cyrillic, Latin and Greek match across case and the
 *   Turkish İ/ı fold to i. Indices never shift because folding is code point to code point.
 * @property wholeWord the match must not continue a word on either side. Word characters are
 *   Unicode letters, digits and combining marks, plus apostrophes that sit between two letters
 *   (so "zbek" is not a whole word inside "o'zbek" or "oʻzbek").
 * @property unifyApostrophes treats ' ʻ ʼ ‘ ’ ` as the same character, because Uzbek writers type
 *   oʻ/gʻ and the tutuq belgisi with whichever apostrophe their keyboard offers.
 */
data class SearchOptions(
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val unifyApostrophes: Boolean = true,
)

/** A match in unit [unitId] covering UTF-16 indices [start, end). */
data class SearchMatch(val unitId: String, val start: Int, val end: Int)

/** Replace UTF-16 indices [start, end) of a text with [replacement]. */
data class TextReplacement(val start: Int, val end: Int, val replacement: String)

object TextSearch {

    /**
     * Finds all non-overlapping occurrences of [query] in [units] (id to text), in unit order and
     * then left to right. Matches never split a surrogate pair. An empty query finds nothing.
     */
    fun findAll(
        units: List<Pair<String, String>>,
        query: String,
        options: SearchOptions = SearchOptions(),
    ): List<SearchMatch> {
        if (query.isEmpty()) return emptyList()
        val needle = codePoints(query).map { fold(it, options) }.toIntArray()
        val result = ArrayList<SearchMatch>()
        for ((id, text) in units) {
            find(text, needle, options) { start, end -> result += SearchMatch(id, start, end) }
        }
        return result
    }

    /** Convenience for a single text; returns UTF-16 ranges [start, end). */
    fun findAll(text: String, query: String, options: SearchOptions = SearchOptions()): List<IntRange> =
        findAll(listOf("" to text), query, options).map { it.start until it.end }

    /**
     * Builds the edits that replace [matches] (all from the unit holding [text]) with [replacement].
     *
     * Matches outside [text] or overlapping an earlier kept match are skipped, so stale results
     * cannot corrupt the text. The result is ordered right to left (descending start): applying the
     * edits in that order one by one never shifts the indices of the edits still to apply.
     */
    fun replacementsFor(text: String, matches: List<SearchMatch>, replacement: String): List<TextReplacement> =
        replacementsForRanges(text, matches.map { it.start until it.end }, replacement)

    fun replacementsForRanges(text: String, ranges: List<IntRange>, replacement: String): List<TextReplacement> {
        val kept = ArrayList<TextReplacement>(ranges.size)
        var lastEnd = 0
        for (range in ranges.sortedBy { it.first }) {
            val start = range.first
            val end = range.last + 1
            if (start < lastEnd || end < start || end > text.length) continue
            kept += TextReplacement(start, end, replacement)
            lastEnd = end
        }
        kept.reverse()
        return kept
    }

    /**
     * Applies [replacements] to [text] in any input order. They must be within bounds and
     * non-overlapping; two insertions at the same index keep their input order.
     */
    fun applyReplacements(text: String, replacements: List<TextReplacement>): String {
        if (replacements.isEmpty()) return text
        val sorted = replacements.sortedBy { it.start }
        val out = StringBuilder(text.length)
        var cursor = 0
        for (r in sorted) {
            require(r.start in 0..r.end && r.end <= text.length) { "Replacement out of bounds: $r" }
            require(r.start >= cursor) { "Overlapping replacement: $r" }
            out.append(text, cursor, r.start).append(r.replacement)
            cursor = r.end
        }
        out.append(text, cursor, text.length)
        return out.toString()
    }

    /**
     * Maps an index of the original text to the edited text, e.g. to keep a caret in place.
     * An index inside a replaced range moves to the end of its replacement.
     */
    fun mapOffset(offset: Int, replacements: List<TextReplacement>): Int {
        var shift = 0
        for (r in replacements.sortedBy { it.start }) {
            when {
                r.end <= offset -> shift += r.replacement.length - (r.end - r.start)
                r.start < offset -> return r.start + shift + r.replacement.length
                else -> break
            }
        }
        return offset + shift
    }

    private inline fun find(text: String, needle: IntArray, options: SearchOptions, onMatch: (Int, Int) -> Unit) {
        val folded = codePoints(text)
        val n = folded.size
        val m = needle.size
        if (m > n) return
        // offsets[i] is the UTF-16 index of code point i; offsets[n] == text.length.
        val offsets = IntArray(n + 1)
        var index = 0
        for (i in 0 until n) {
            offsets[i] = index
            index += Character.charCount(folded[i])
        }
        offsets[n] = text.length
        val raw = folded.copyOf()
        for (i in 0 until n) folded[i] = fold(folded[i], options)

        var i = 0
        while (i <= n - m) {
            var j = 0
            while (j < m && folded[i + j] == needle[j]) j++
            if (j == m && (!options.wholeWord || isBoundary(raw, i) && isBoundary(raw, i + m))) {
                onMatch(offsets[i], offsets[i + m])
                i += m
            } else {
                i++
            }
        }
    }

    /** True when the gap before code point [pos] does not sit inside a word. */
    private fun isBoundary(cps: IntArray, pos: Int): Boolean =
        pos == 0 || pos == cps.size || !isWordChar(cps, pos - 1) || !isWordChar(cps, pos)

    private fun isWordChar(cps: IntArray, i: Int): Boolean {
        val cp = cps[i]
        if (isApostrophe(cp)) {
            return i > 0 && i < cps.lastIndex && Character.isLetter(cps[i - 1]) && Character.isLetter(cps[i + 1])
        }
        if (Character.isLetterOrDigit(cp)) return true
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun fold(cp: Int, options: SearchOptions): Int = when {
        options.unifyApostrophes && isApostrophe(cp) -> MODIFIER_TURNED_COMMA
        options.matchCase -> cp
        else -> Character.toLowerCase(Character.toUpperCase(cp))
    }

    // All apostrophes, including the letter-like ʻ and ʼ, count as word characters only between
    // two letters, so a word quoted with them is still found as a whole word.
    private fun isApostrophe(cp: Int): Boolean = cp in APOSTROPHES

    private fun codePoints(s: String): IntArray = s.codePoints().toArray()

    private const val MODIFIER_TURNED_COMMA = 0x02BB
    private val APOSTROPHES = intArrayOf(0x0027, 0x0060, 0x02BB, 0x02BC, 0x2018, 0x2019)
}
