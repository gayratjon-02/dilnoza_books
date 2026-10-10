package dilnoza.books.ui.editor.page

import dilnoza.books.editor.layout.FlowItem
import dilnoza.books.editor.layout.PagePlan
import dilnoza.books.editor.layout.PageSpec
import dilnoza.books.editor.layout.Paginator
import dilnoza.books.ui.editor.PageGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPagesTest {

    private val spec = PageSpec(widthPt = 100f, heightPt = 120f, marginPt = 10f)

    private fun pages(vararg items: FlowItem): EditorPages {
        val pagination = Paginator(spec.contentHeightPt).paginate(items.toList())
        return EditorPages.of(PagePlan.build(spec, 20f, items.map { it.blockId }, pagination), items.toList())
    }

    private fun text(id: String, lines: Int) =
        FlowItem.Text(id, List(lines) { 10f }, lineStarts = List(lines) { it * 40 })

    @Test
    fun `gaps of a split paragraph are the offsets of the lines after each page break`() {
        val pages = pages(text("a", 25))

        val gaps = pages.gapsOf("a")
        assertEquals(2, gaps.size)
        assertEquals(PageGap(400, gaps[0].heightPt), gaps[0])
        assertEquals(800, gaps[1].offset)
        assertTrue(pages.gapsOf("missing").isEmpty())
    }

    @Test
    fun `an edit that changes no line is equal to the pagination before it`() {
        // A typed letter changes the text but not the lines: equal pages must not notify readers.
        assertEquals(pages(text("a", 25), text("b", 3)), pages(text("a", 25), text("b", 3)))
        assertNotEquals(pages(text("a", 25), text("b", 3)), pages(text("a", 25), text("b", 4)))
    }
}
