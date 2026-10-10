package dilnoza.books.editor.layout

import dilnoza.books.domain.model.PageSize
import kotlin.math.floor

/**
 * Physical page in typographic points (1/72 inch). The editor, the paginator and the PDF export
 * all work in these units, so a page looks the same on every screen and in the exported file.
 */
data class PageSpec(
    val widthPt: Float,
    val heightPt: Float,
    val marginPt: Float,
) {
    val contentWidthPt: Float get() = widthPt - 2 * marginPt
    val contentHeightPt: Float get() = heightPt - 2 * marginPt

    /**
     * The text column in whole points. Layout works in whole pixels (1px == 1pt), so lines are both
     * measured and drawn at exactly this width; a fractional width would be rounded differently on
     * each side and wrap some lines one word earlier on screen than on the planned page.
     */
    val columnWidthPt: Int get() = floor(contentWidthPt + WIDTH_EPSILON).toInt()

    companion object {
        const val MIN_MARGIN_MM = 10
        const val MAX_MARGIN_MM = 35

        fun of(size: PageSize, marginMm: Int): PageSpec {
            val (width, height) = when (size) {
                PageSize.A5 -> mmToPt(148f) to mmToPt(210f)
                PageSize.A4 -> mmToPt(210f) to mmToPt(297f)
                PageSize.LETTER -> 8.5f * POINTS_PER_INCH to 11f * POINTS_PER_INCH
            }
            return PageSpec(width, height, mmToPt(marginMm.coerceIn(MIN_MARGIN_MM, MAX_MARGIN_MM).toFloat()))
        }

        fun mmToPt(mm: Float): Float = mm / MM_PER_INCH * POINTS_PER_INCH

        private const val WIDTH_EPSILON = 0.001f
        private const val POINTS_PER_INCH = 72f
        private const val MM_PER_INCH = 25.4f
    }
}

/** Typographic scale of page content, in points. */
object PageTypography {
    const val TITLE_SIZE_PT = 22f
    const val TITLE_SPACE_AFTER_PT = 14f
    const val BODY_SIZE_PT = 11f
    const val LINE_HEIGHT = 1.45f
    const val PARAGRAPH_SPACING_PT = 6f
    const val HEADING_SPACE_BEFORE_PT = 14f
    const val HEADING_SPACE_AFTER_PT = 6f
    const val CAPTION_SIZE_PT = 9f
    const val PAGE_NUMBER_SIZE_PT = 9f

    fun headingSizePt(level: Int): Float = when (level) {
        1 -> 20f
        2 -> 16f
        else -> 13.5f
    }
}
