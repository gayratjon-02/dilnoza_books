package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {

    private fun paragraph(id: String, text: String) = EditorBlock(id, BlockKind.PARAGRAPH, RichText(text))

    @Test
    fun `restoring the same content changes nothing`() {
        val blocks = mutableListOf(paragraph("a", "One"), paragraph("b", "Two"))
        val target = blocks.map { it.snapshot() }

        val result = restoreBlocks(blocks, target)

        assertEquals(RestoreResult(emptySet(), emptySet(), emptySet(), reordered = false), result)
    }

    @Test
    fun `text and spans come back and the block instance is kept`() {
        val bold = RichText("Hello", listOf(StyleSpan(0, 5, SpanStyleData(bold = true))))
        val block = EditorBlock("a", BlockKind.PARAGRAPH, bold)
        val target = listOf(block.snapshot())
        block.setRich(RichText("Hello world"))
        block.kind = BlockKind.QUOTE
        val blocks = mutableListOf(block)

        val result = restoreBlocks(blocks, target)

        assertSame(block, blocks.single())
        assertEquals(bold, block.rich)
        assertEquals("Hello", block.value.text)
        assertEquals(BlockKind.PARAGRAPH, block.kind)
        assertEquals(setOf("a"), result.changedIds)
        assertFalse(result.reordered)
    }

    @Test
    fun `the selection is kept inside the restored text`() {
        val block = paragraph("a", "Hi")
        val target = listOf(block.snapshot())
        block.setRich(RichText("Hi there"), TextRange(8))

        restoreBlocks(mutableListOf(block), target)

        assertEquals(TextRange(2), block.value.selection)
    }

    @Test
    fun `a removed block is brought back in its place`() {
        val blocks = mutableListOf(paragraph("a", "One"), paragraph("b", "Two"), paragraph("c", "Three"))
        val target = blocks.map { it.snapshot() }
        blocks.removeAt(1)

        val result = restoreBlocks(blocks, target)

        assertEquals(listOf("a", "b", "c"), blocks.map { it.id })
        assertEquals("Two", blocks[1].value.text)
        assertEquals(setOf("b"), result.addedIds)
        assertTrue(result.reordered)
    }

    @Test
    fun `an added block is removed`() {
        val blocks = mutableListOf(paragraph("a", "One"))
        val target = blocks.map { it.snapshot() }
        blocks.add(paragraph("b", "Two"))

        val result = restoreBlocks(blocks, target)

        assertEquals(listOf("a"), blocks.map { it.id })
        assertEquals(setOf("b"), result.removedIds)
        assertTrue(result.reordered)
    }

    @Test
    fun `a moved block goes back`() {
        val blocks = mutableListOf(paragraph("a", "One"), paragraph("b", "Two"))
        val target = blocks.map { it.snapshot() }
        blocks.add(0, blocks.removeAt(1))

        val result = restoreBlocks(blocks, target)

        assertEquals(listOf("a", "b"), blocks.map { it.id })
        assertTrue(result.reordered)
        assertTrue(result.changedIds.isEmpty())
    }

    @Test
    fun `table, alignment and list indent come back`() {
        val table = TableState.create(2, 2, headerRow = true)
        val block = EditorBlock("t", BlockKind.TABLE, table = table)
        val item = EditorBlock("l", BlockKind.BULLET, RichText("x"), indent = 2, align = TextAlignment.CENTER)
        val target = listOf(block.snapshot(), item.snapshot())
        block.table = table.appendRow().first
        item.indent = 0
        item.align = TextAlignment.START

        val result = restoreBlocks(mutableListOf(block, item), target)

        assertEquals(table, block.table)
        assertEquals(2, item.indent)
        assertEquals(TextAlignment.CENTER, item.align)
        assertEquals(setOf("t", "l"), result.changedIds)
    }

    @Test
    fun `a picture still importing in the snapshot keeps its stored file`() {
        val placeholder = EditorBlock("i", BlockKind.IMAGE, image = ImageState.Pending)
        val target = listOf(placeholder.snapshot())
        val stored = ImageState("pic.jpg", 400, 300)

        val blocks = mutableListOf<EditorBlock>()
        restoreBlocks(blocks, target) { stored }

        assertEquals(stored, blocks.single().image)
    }

    @Test
    fun `a picture whose import failed is not brought back`() {
        val target = listOf(EditorBlock("i", BlockKind.IMAGE, image = ImageState.Pending).snapshot())
        val blocks = mutableListOf(paragraph("a", "One"))

        restoreBlocks(blocks, target) { null }

        assertTrue(blocks.isEmpty())
    }
}
