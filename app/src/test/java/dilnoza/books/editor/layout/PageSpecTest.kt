package dilnoza.books.editor.layout

import dilnoza.books.domain.model.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSpecTest {

    private val delta = 0.01f

    @Test
    fun `iso sizes are converted from millimetres`() {
        val a5 = PageSpec.of(PageSize.A5, marginMm = 20)
        val a4 = PageSpec.of(PageSize.A4, marginMm = 20)

        assertEquals(419.53f, a5.widthPt, delta)
        assertEquals(595.28f, a5.heightPt, delta)
        assertEquals(595.28f, a4.widthPt, delta)
        assertEquals(841.89f, a4.heightPt, delta)
    }

    @Test
    fun `letter is defined in inches`() {
        val letter = PageSpec.of(PageSize.LETTER, marginMm = 20)

        assertEquals(612f, letter.widthPt, delta)
        assertEquals(792f, letter.heightPt, delta)
    }

    @Test
    fun `content box excludes margins on both sides`() {
        val spec = PageSpec.of(PageSize.A5, marginMm = 20)

        assertEquals(56.69f, spec.marginPt, delta)
        assertEquals(spec.widthPt - 2 * spec.marginPt, spec.contentWidthPt, delta)
        assertEquals(306.14f, spec.contentWidthPt, delta)
        assertEquals(481.89f, spec.contentHeightPt, delta)
    }

    @Test
    fun `text column is the content width rounded down to whole points`() {
        // 306.14 pt: measuring at 307 but drawing at 306 would wrap lines differently.
        assertEquals(306, PageSpec.of(PageSize.A5, marginMm = 20).columnWidthPt)
        // 334.48 pt and 538.58 pt: rounding up or to nearest would both disagree with one side.
        assertEquals(334, PageSpec.of(PageSize.A5, marginMm = 15).columnWidthPt)
        assertEquals(538, PageSpec.of(PageSize.A4, marginMm = 10).columnWidthPt)
        for (size in PageSize.entries) {
            for (margin in PageSpec.MIN_MARGIN_MM..PageSpec.MAX_MARGIN_MM) {
                val spec = PageSpec.of(size, margin)
                assertTrue(spec.columnWidthPt <= spec.contentWidthPt + 0.001f)
                assertTrue(spec.columnWidthPt > spec.contentWidthPt - 1f)
            }
        }
    }

    @Test
    fun `pictures get whole point sizes`() {
        assertEquals(76, PageStyles.imageWidthPt(306, 25))
        assertEquals(306, PageStyles.imageWidthPt(306, 100))
        // 306 wide at 4:3 is 229.5 tall, rounded as the layout's aspect ratio rounds it.
        assertEquals(230, PageStyles.imageHeightPt(306, 400, 300))
        assertEquals(612, PageStyles.imageHeightPt(306, 100, 200))
    }

    @Test
    fun `margins are clamped to a usable range`() {
        assertEquals(PageSpec.mmToPt(10f), PageSpec.of(PageSize.A5, marginMm = 0).marginPt, delta)
        assertEquals(PageSpec.mmToPt(35f), PageSpec.of(PageSize.A5, marginMm = 200).marginPt, delta)
    }

    @Test
    fun `heading sizes shrink with level`() {
        assertEquals(20f, PageTypography.headingSizePt(1), delta)
        assertEquals(16f, PageTypography.headingSizePt(2), delta)
        assertEquals(13.5f, PageTypography.headingSizePt(3), delta)
    }
}
