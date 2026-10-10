package dilnoza.books.editor.layout

/** One measured block of the chapter flow; all sizes in points. */
sealed interface FlowItem {
    val blockId: String
    val spaceBeforePt: Float
    val spaceAfterPt: Float

    /** Text that may be split between pages at line boundaries. */
    data class Text(
        override val blockId: String,
        val lineHeightsPt: List<Float>,
        override val spaceBeforePt: Float = 0f,
        override val spaceAfterPt: Float = 0f,
        /** Headings: never end a page, move to the next page together with the following block. */
        val keepWithNext: Boolean = false,
        /** Character offset where each line starts, so a page gap can be inserted before a line. */
        val lineStarts: List<Int> = emptyList(),
    ) : FlowItem

    /** Image, table or divider: moves to the next page as a whole; scaled down if taller than a page. */
    data class Box(
        override val blockId: String,
        val heightPt: Float,
        override val spaceBeforePt: Float = 0f,
        override val spaceAfterPt: Float = 0f,
    ) : FlowItem

    /** Manual "new page". */
    data class Break(override val blockId: String) : FlowItem {
        override val spaceBeforePt: Float get() = 0f
        override val spaceAfterPt: Float get() = 0f
    }
}

/**
 * Part of a block placed on one page. [topPt] is measured from the top of the page's content box.
 * Text fragments cover lines [firstLine, endLine); boxes use 0..1. [scale] < 1 when a box was shrunk to fit.
 */
data class Fragment(
    val blockId: String,
    val pageIndex: Int,
    val topPt: Float,
    val heightPt: Float,
    val firstLine: Int = 0,
    val endLine: Int = 1,
    val scale: Float = 1f,
)

data class Pagination(
    val pageCount: Int,
    val fragments: List<Fragment>,
    val breakIds: Set<String> = emptySet(),
) {
    fun fragmentsOf(blockId: String): List<Fragment> = fragments.filter { it.blockId == blockId }

    fun isBreak(blockId: String): Boolean = blockId in breakIds
}

/**
 * Lays the chapter flow out on pages of [contentHeightPt]. Pure and deterministic, so the editor
 * and the exporters produce identical pages from identical measurements.
 */
class Paginator(
    private val contentHeightPt: Float,
    private val minLinesAtPageEdge: Int = 2,
) {
    init {
        require(contentHeightPt > 0f) { "Page content height must be positive" }
    }

    fun paginate(items: List<FlowItem>): Pagination {
        val fragments = mutableListOf<Fragment>()
        var page = 0
        var y = 0f
        var pageHasContent = false

        fun newPage() {
            page++
            y = 0f
            pageHasContent = false
        }

        // Space above a block disappears at the top of a page, as in print.
        fun spaceBefore(item: FlowItem) = if (pageHasContent) item.spaceBeforePt else 0f

        for ((index, item) in items.withIndex()) {
            when (item) {
                is FlowItem.Break -> {
                    fragments += Fragment(item.blockId, page, y, 0f)
                    if (pageHasContent) newPage()
                }

                is FlowItem.Box -> {
                    val scale = if (item.heightPt > contentHeightPt) contentHeightPt / item.heightPt else 1f
                    val height = item.heightPt * scale
                    if (pageHasContent && y + spaceBefore(item) + height > contentHeightPt) newPage()
                    y += spaceBefore(item)
                    fragments += Fragment(item.blockId, page, y, height, scale = scale)
                    y += height + item.spaceAfterPt
                    pageHasContent = true
                }

                is FlowItem.Text -> {
                    val lines = item.lineHeightsPt
                    if (item.keepWithNext && pageHasContent) {
                        val needed = spaceBefore(item) + lines.sum() + item.spaceAfterPt + leadOf(items.getOrNull(index + 1))
                        if (y + needed > contentHeightPt) newPage()
                    }
                    if (lines.isEmpty()) {
                        y += spaceBefore(item)
                        fragments += Fragment(item.blockId, page, y, 0f, 0, 0)
                        y += item.spaceAfterPt
                        pageHasContent = true
                        continue
                    }

                    var line = 0
                    while (line < lines.size) {
                        val top = y + spaceBefore(item)
                        val remaining = lines.size - line
                        var fit = countFitting(lines, line, contentHeightPt - top)

                        if (fit < remaining) {
                            // Widow/orphan control: keep at least N lines on each side of a page break.
                            val minEdge = minOf(minLinesAtPageEdge, remaining)
                            if (remaining - fit < minEdge) fit = remaining - minEdge
                            if (fit < minEdge && pageHasContent) fit = 0
                            // A page must always take at least one line, or a huge line would loop forever.
                            if (fit <= 0) {
                                if (pageHasContent) {
                                    newPage()
                                    continue
                                }
                                fit = maxOf(1, countFitting(lines, line, contentHeightPt))
                            }
                        }

                        val end = line + fit
                        val height = lines.subList(line, end).sum()
                        fragments += Fragment(item.blockId, page, top, height, line, end)
                        y = top + height
                        pageHasContent = true
                        line = end
                        if (line < lines.size) newPage()
                    }
                    y += item.spaceAfterPt
                }
            }
        }
        return Pagination(
            pageCount = page + 1,
            fragments = fragments,
            breakIds = items.filterIsInstance<FlowItem.Break>().mapTo(HashSet()) { it.blockId },
        )
    }

    /** Minimum height the next block needs on the same page for a heading to stay with it. */
    private fun leadOf(next: FlowItem?): Float = when (next) {
        is FlowItem.Text -> next.spaceBeforePt + next.lineHeightsPt.take(minLinesAtPageEdge).sum()
        is FlowItem.Box -> next.spaceBeforePt + minOf(next.heightPt, contentHeightPt)
        else -> 0f
    }

    private fun countFitting(lines: List<Float>, from: Int, available: Float): Int {
        var used = 0f
        var count = 0
        for (i in from until lines.size) {
            if (used + lines[i] > available + EPSILON) break
            used += lines[i]
            count++
        }
        return count
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
