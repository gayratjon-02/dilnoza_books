package dilnoza.books.editor.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePlanTest {

    // 100pt content box, 10pt margins: pages are 120pt tall, 20pt apart.
    private val spec = PageSpec(widthPt = 100f, heightPt = 120f, marginPt = 10f)
    private val gap = 20f
    private val paginator = Paginator(spec.contentHeightPt)

    private fun text(id: String, lines: Int, before: Float = 0f, after: Float = 0f) =
        FlowItem.Text(id, List(lines) { 10f }, before, after)

    private fun plan(vararg items: FlowItem): PagePlan =
        PagePlan.build(spec, gap, items.map { it.blockId }, paginator.paginate(items.toList()))

    @Test
    fun `first block starts below the top margin`() {
        val plan = plan(text("a", 3))

        val a = plan.blocks.getValue("a")
        assertEquals(0f, a.startPt, 0.01f)
        assertEquals(10f, a.leadPt, 0.01f)
        assertEquals(30f, a.heightPt, 0.01f)
        assertEquals(120f - 40f, plan.tailPt, 0.01f)
    }

    @Test
    fun `box taller than a page keeps the scale it is drawn at`() {
        val plan = plan(text("a", 2), FlowItem.Box("table", heightPt = 400f), FlowItem.Box("small", heightPt = 20f))

        val table = plan.blocks.getValue("table")
        assertEquals(0.25f, table.scale, 0.001f)
        assertEquals(100f, table.heightPt, 0.01f)
        assertEquals(1f, plan.blocks.getValue("small").scale, 0.001f)
        assertEquals(1f, plan.blocks.getValue("a").scale, 0.001f)
    }

    @Test
    fun `spacing between blocks becomes lead`() {
        val plan = plan(text("a", 2, after = 4f), text("b", 2, before = 6f))

        val b = plan.blocks.getValue("b")
        assertEquals(30f, b.startPt, 0.01f)
        assertEquals(10f, b.leadPt, 0.01f)
    }

    @Test
    fun `block on the next page leads over the rest of the page and the gap`() {
        val plan = plan(text("a", 10), text("b", 2))

        val b = plan.blocks.getValue("b")
        // a ends at 110 (page bottom content edge); b starts at 140 + 10.
        assertEquals(110f, b.startPt, 0.01f)
        assertEquals(10f + 20f + 10f, b.leadPt, 0.01f)
        assertEquals(2, plan.pageCount)
    }

    @Test
    fun `split paragraph keeps one block with an inner gap`() {
        val plan = plan(text("a", 5), text("b", 9))

        val b = plan.blocks.getValue("b")
        // Page 1 has room for 5 of b's 9 lines; the other 4 continue on page 2.
        assertEquals(1, b.innerGaps.size)
        val gapBefore = b.innerGaps.single()
        assertEquals(5, gapBefore.beforeLine)
        // From the bottom of page 1's last line (110) to page 2's content top (150).
        assertEquals(40f, gapBefore.heightPt, 0.01f)
        assertEquals(90f + 40f, b.heightPt, 0.01f)
    }

    @Test
    fun `manual break owns the rest of its page`() {
        val plan = plan(text("a", 2), FlowItem.Break("br"), text("b", 1))

        val br = plan.blocks.getValue("br")
        assertEquals(30f, br.startPt, 0.01f)
        assertEquals(0f, br.leadPt, 0.01f)
        assertEquals(110f - 30f, br.heightPt, 0.01f)
        assertEquals(10f + 20f + 10f, plan.blocks.getValue("b").leadPt, 0.01f)
    }

    @Test
    fun `items tile the document without gaps or overlaps`() {
        val items = (0 until 30).map { i ->
            when {
                i % 11 == 5 -> FlowItem.Break("br$i")
                i % 5 == 2 -> FlowItem.Box("box$i", 40f, 4f, 4f)
                else -> text("t$i", 1 + i % 13, before = 6f, after = 6f)
            }
        }
        val plan = PagePlan.build(spec, gap, items.map { it.blockId }, paginator.paginate(items))

        var expectedStart = 0f
        items.forEach { item ->
            val block = plan.blocks.getValue(item.blockId)
            assertEquals("${item.blockId} start", expectedStart, block.startPt, 0.01f)
            assertTrue(block.leadPt >= 0f && block.heightPt >= 0f)
            expectedStart = block.endPt
        }
        assertEquals(plan.documentHeightPt, expectedStart + plan.tailPt, 0.01f)
    }
}
