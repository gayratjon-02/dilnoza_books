package dilnoza.books.editor.layout

import kotlin.math.roundToInt

/**
 * Turns a [Pagination] into a vertical "document" where pages are stacked with a [gapPt] between
 * them, and tells every block where it sits in it. The editor renders blocks one after another, so
 * each block item gets a [BlockPlan]: empty space above it ([BlockPlan.leadPt]), its own height and
 * the page gaps that fall inside it. Units are points; y = 0 is the top edge of the first page.
 */
data class PagePlan(
    val spec: PageSpec,
    val gapPt: Float,
    val pageCount: Int,
    val blocks: Map<String, BlockPlan>,
    /** Space after the last block down to the bottom edge of the last page. */
    val tailPt: Float,
) {
    val pageStridePt: Float get() = spec.heightPt + gapPt
    val documentHeightPt: Float get() =
        (pageCount * spec.heightPt + (pageCount - 1).coerceAtLeast(0) * gapPt).roundToInt().toFloat()

    fun pageTop(page: Int): Float = (page * pageStridePt).roundToInt().toFloat()

    fun pageBottom(page: Int): Float = (page * pageStridePt + spec.heightPt).roundToInt().toFloat()

    companion object {
        fun build(spec: PageSpec, gapPt: Float, order: List<String>, pagination: Pagination): PagePlan {
            // Whole points: block items are laid out in whole units, so they tile the pages without seams.
            fun docY(page: Int, y: Float) = (page * (spec.heightPt + gapPt) + spec.marginPt + y).roundToInt().toFloat()
            val byBlock = pagination.fragments.groupBy { it.blockId }
            val plans = LinkedHashMap<String, BlockPlan>()
            var end = 0f

            for ((index, id) in order.withIndex()) {
                val fragments = byBlock[id].orEmpty()
                val first = fragments.firstOrNull()
                if (first == null) {
                    plans[id] = BlockPlan(startPt = end, leadPt = 0f, heightPt = 0f, innerGaps = emptyList())
                    continue
                }
                val isBreak = pagination.isBreak(id)
                val top = docY(first.pageIndex, first.topPt)
                val lead = (top - end).coerceAtLeast(0f)
                val gaps = fragments.zipWithNext { previous, next ->
                    InnerGap(
                        beforeLine = next.firstLine,
                        heightPt = docY(next.pageIndex, next.topPt) - docY(previous.pageIndex, previous.topPt + previous.heightPt),
                    )
                }
                val last = fragments.last()
                var bottom = docY(last.pageIndex, last.topPt + last.heightPt)
                if (isBreak) {
                    // A manual break owns the rest of its page when the next block starts on a later page.
                    val nextPage = order.getOrNull(index + 1)?.let { byBlock[it]?.firstOrNull()?.pageIndex }
                    if (nextPage == null || nextPage > first.pageIndex) bottom = docY(first.pageIndex, spec.contentHeightPt)
                }
                plans[id] = BlockPlan(startPt = end, leadPt = lead, heightPt = bottom - top, innerGaps = gaps)
                end = top + (bottom - top)
            }

            val pageCount = pagination.pageCount
            val documentEnd = (pageCount * spec.heightPt + (pageCount - 1) * gapPt).roundToInt().toFloat()
            return PagePlan(spec, gapPt, pageCount, plans, tailPt = (documentEnd - end).coerceAtLeast(0f))
        }
    }
}

/**
 * Placement of one block. The block item spans [startPt, startPt + leadPt + heightPt) in the document;
 * its content begins after [leadPt] of empty space.
 */
data class BlockPlan(
    val startPt: Float,
    val leadPt: Float,
    val heightPt: Float,
    /** Page gaps inside a text block that continues on the next page, before the given line. */
    val innerGaps: List<InnerGap>,
) {
    val endPt: Float get() = startPt + leadPt + heightPt
}

data class InnerGap(val beforeLine: Int, val heightPt: Float)
