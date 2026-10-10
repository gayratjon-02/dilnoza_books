package dilnoza.books.editor.commands

enum class ListKind { NONE, BULLET, ORDERED }

/** One block in document order; [indent] is clamped to 0..[ListNumbering.MAX_INDENT]. */
data class ListItem(val kind: ListKind, val indent: Int = 0) {
    companion object {
        val NONE = ListItem(ListKind.NONE)
    }
}

object ListNumbering {

    const val MAX_INDENT = 2

    private val bullets = listOf("•", "◦", "▪")
    private const val MAX_ROMAN = 3999

    /**
     * Display labels for consecutive blocks: null for non-list blocks, a bullet glyph by indent,
     * or "1." / "a." / "i." for ordered items at indent 0 / 1 / 2.
     * Numbering restarts after any non-list block, after a bullet at the same or a shallower level,
     * and nested levels restart whenever an item at a shallower level appears.
     */
    fun labels(items: List<ListItem>): List<String?> {
        val counters = IntArray(MAX_INDENT + 1)
        return items.map { item ->
            val level = item.indent.coerceIn(0, MAX_INDENT)
            when (item.kind) {
                ListKind.NONE -> {
                    counters.fill(0)
                    null
                }
                ListKind.BULLET -> {
                    counters.fill(0, level)
                    bullets[level]
                }
                ListKind.ORDERED -> {
                    counters.fill(0, level + 1)
                    counters[level]++
                    "${format(counters[level], level)}."
                }
            }
        }
    }

    private fun format(n: Int, level: Int): String = when (level) {
        0 -> n.toString()
        1 -> alphabetic(n)
        else -> roman(n)
    }

    // Bijective base-26: 26 -> z, 27 -> aa.
    private fun alphabetic(n: Int): String {
        val sb = StringBuilder()
        var x = n
        while (x > 0) {
            x--
            sb.append('a' + x % 26)
            x /= 26
        }
        return sb.reverse().toString()
    }

    private val romanValues = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    private val romanSymbols = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")

    private fun roman(n: Int): String {
        if (n > MAX_ROMAN) return n.toString()
        val sb = StringBuilder()
        var x = n
        for (i in romanValues.indices) {
            while (x >= romanValues[i]) {
                sb.append(romanSymbols[i])
                x -= romanValues[i]
            }
        }
        return sb.toString()
    }
}
