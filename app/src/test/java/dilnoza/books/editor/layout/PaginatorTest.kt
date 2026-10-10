package dilnoza.books.editor.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorTest {

    // Page holds exactly 10 lines of 10pt.
    private val paginator = Paginator(contentHeightPt = 100f)

    private fun text(id: String, lines: Int, before: Float = 0f, after: Float = 0f, keep: Boolean = false) =
        FlowItem.Text(id, List(lines) { 10f }, before, after, keep)

    private fun Pagination.pagesOf(id: String) = fragmentsOf(id).map { it.pageIndex }

    @Test
    fun `empty chapter still has one page`() {
        assertEquals(1, paginator.paginate(emptyList()).pageCount)
    }

    @Test
    fun `short text stays on the first page`() {
        val result = paginator.paginate(listOf(text("a", 3), text("b", 4)))

        assertEquals(1, result.pageCount)
        assertEquals(30f, result.fragmentsOf("b").single().topPt, 0.01f)
    }

    @Test
    fun `long paragraph flows over several pages at line boundaries`() {
        val result = paginator.paginate(listOf(text("a", 25)))

        assertEquals(3, result.pageCount)
        val parts = result.fragmentsOf("a")
        assertEquals(listOf(0 to 10, 10 to 20, 20 to 25), parts.map { it.firstLine to it.endLine })
        assertTrue(parts.all { it.topPt == 0f })
    }

    @Test
    fun `a single orphan line is not left at the bottom of a page`() {
        // 9 lines used, 1 free: a 5-line paragraph must not leave its first line alone.
        val result = paginator.paginate(listOf(text("a", 9), text("b", 5)))

        assertEquals(listOf(1), result.pagesOf("b"))
        assertEquals(0 to 5, result.fragmentsOf("b").single().let { it.firstLine to it.endLine })
    }

    @Test
    fun `a single widow line is not carried to the next page`() {
        // 2 lines used, 8 free: an 9-line paragraph would leave 1 line; pull one more over.
        val result = paginator.paginate(listOf(text("a", 2), text("b", 9)))

        val parts = result.fragmentsOf("b")
        assertEquals(listOf(0 to 7, 7 to 9), parts.map { it.firstLine to it.endLine })
    }

    @Test
    fun `space before is dropped at the top of a page`() {
        val result = paginator.paginate(listOf(text("a", 10), text("b", 2, before = 6f)))

        val b = result.fragmentsOf("b").single()
        assertEquals(1, b.pageIndex)
        assertEquals(0f, b.topPt, 0.01f)
    }

    @Test
    fun `space before is kept inside a page`() {
        val result = paginator.paginate(listOf(text("a", 2), text("b", 2, before = 6f)))

        assertEquals(26f, result.fragmentsOf("b").single().topPt, 0.01f)
    }

    @Test
    fun `box that does not fit moves to the next page whole`() {
        val result = paginator.paginate(listOf(text("a", 6), FlowItem.Box("img", 50f)))

        val image = result.fragmentsOf("img").single()
        assertEquals(1, image.pageIndex)
        assertEquals(0f, image.topPt, 0.01f)
        assertEquals(1f, image.scale, 0.001f)
    }

    @Test
    fun `box taller than a page is scaled down to fit`() {
        val result = paginator.paginate(listOf(FlowItem.Box("img", 250f)))

        val image = result.fragmentsOf("img").single()
        assertEquals(100f, image.heightPt, 0.01f)
        assertEquals(0.4f, image.scale, 0.001f)
        assertEquals(1, result.pageCount)
    }

    @Test
    fun `manual break starts a new page`() {
        val result = paginator.paginate(listOf(text("a", 2), FlowItem.Break("br"), text("b", 2)))

        assertEquals(2, result.pageCount)
        assertEquals(listOf(1), result.pagesOf("b"))
        assertEquals(0, result.fragmentsOf("br").single().pageIndex)
    }

    @Test
    fun `break on an empty page does not create a blank page`() {
        val result = paginator.paginate(listOf(FlowItem.Break("br"), text("a", 2)))

        assertEquals(1, result.pageCount)
        assertEquals(listOf(0), result.pagesOf("a"))
    }

    @Test
    fun `heading moves with the following paragraph`() {
        // 8 lines used: heading (1) + first 2 lines of next would need 3.
        val result = paginator.paginate(listOf(text("a", 8), text("h", 1, keep = true), text("b", 5)))

        assertEquals(listOf(1), result.pagesOf("h"))
        assertEquals(listOf(1), result.pagesOf("b"))
    }

    @Test
    fun `heading stays when the next paragraph start fits`() {
        val result = paginator.paginate(listOf(text("a", 5), text("h", 1, keep = true), text("b", 8)))

        assertEquals(listOf(0), result.pagesOf("h"))
        assertEquals(listOf(0, 1), result.pagesOf("b"))
    }

    @Test
    fun `line taller than a page still makes progress`() {
        val result = Paginator(contentHeightPt = 100f).paginate(
            listOf(FlowItem.Text("a", listOf(150f, 10f, 10f))),
        )

        assertEquals(listOf(0 to 1, 1 to 3), result.fragmentsOf("a").map { it.firstLine to it.endLine })
    }

    @Test
    fun `empty paragraph occupies no lines but keeps its place`() {
        val result = paginator.paginate(listOf(text("a", 3), FlowItem.Text("empty", emptyList())))

        val empty = result.fragmentsOf("empty").single()
        assertEquals(0, empty.pageIndex)
        assertEquals(30f, empty.topPt, 0.01f)
    }

    @Test
    fun `fragments never exceed the page content height`() {
        val items = (0 until 40).map { i ->
            if (i % 7 == 3) FlowItem.Box("box$i", 35f + i, spaceBeforePt = 4f, spaceAfterPt = 4f)
            else text("t$i", 1 + (i * 5) % 13, before = 6f, after = 6f, keep = i % 9 == 0)
        }

        val result = paginator.paginate(items)

        result.fragments.forEach { f ->
            assertTrue("$f overflows", f.topPt + f.heightPt <= 100f + 0.01f)
        }
        // Every line of every text block is placed exactly once, in order.
        items.filterIsInstance<FlowItem.Text>().forEach { item ->
            val parts = result.fragmentsOf(item.blockId)
            assertEquals(0, parts.first().firstLine)
            assertEquals(item.lineHeightsPt.size, parts.last().endLine)
            parts.zipWithNext().forEach { (a, b) -> assertEquals(a.endLine, b.firstLine) }
        }
    }
}
