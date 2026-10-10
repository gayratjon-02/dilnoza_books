package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEditingTest {

    private var nextId = 0
    private val blocks = mutableListOf<EditorBlock>()
    private val editing = BlockEditing(blocks) { kind, rich -> EditorBlock("n${nextId++}", kind, rich, TextRange(0)) }

    private fun block(id: String, kind: BlockKind, text: String = "", rich: RichText = RichText(text)) =
        EditorBlock(id, kind, rich).also { blocks.add(it) }

    private fun EditorBlock.type(text: String, cursor: Int) =
        editing.type(this, TextFieldValue(text, TextRange(cursor)), null)

    private val texts get() = blocks.map { it.kind to it.rich.text }

    @Test
    fun `enter in the middle splits a paragraph and keeps spans on both halves`() {
        val bold = SpanStyleData(bold = true)
        val a = block("a", BlockKind.PARAGRAPH, rich = RichText("Salom dunyo", listOf(StyleSpan(3, 9, bold))))

        val edit = a.type("Salom\n dunyo", 6)

        assertEquals(listOf(BlockKind.PARAGRAPH to "Salom", BlockKind.PARAGRAPH to " dunyo"), texts)
        assertEquals(listOf(StyleSpan(3, 5, bold)), blocks[0].rich.spans)
        assertEquals(listOf(StyleSpan(0, 4, bold)), blocks[1].rich.spans)
        assertEquals(BlockEdit.Structure(focusId = blocks[1].id), edit)
        assertEquals(TextRange(0), blocks[1].value.selection)
    }

    @Test
    fun `enter in a list item continues the list at the same indent`() {
        val a = block("a", BlockKind.NUMBERED, "Birinchi").apply { indent = 1 }

        a.type("Birinchi\n", 9)

        assertEquals(listOf(BlockKind.NUMBERED to "Birinchi", BlockKind.NUMBERED to ""), texts)
        assertEquals(1, blocks[1].indent)
    }

    @Test
    fun `enter on an empty list item outdents it, then turns it into a paragraph`() {
        val a = block("a", BlockKind.BULLET).apply { indent = 1 }

        assertEquals(BlockEdit.Content("a"), a.type("\n", 1))
        assertEquals(0, a.indent)
        assertEquals(BlockKind.BULLET, a.kind)

        a.type("\n", 1)
        assertEquals(listOf(BlockKind.PARAGRAPH to ""), texts)
    }

    @Test
    fun `enter on an empty quote ends the quote`() {
        val a = block("a", BlockKind.QUOTE)

        a.type("\n", 1)

        assertEquals(listOf(BlockKind.PARAGRAPH to ""), texts)
    }

    @Test
    fun `enter at the end of a heading starts a paragraph`() {
        val a = block("a", BlockKind.HEADING, "Bob").apply { align = TextAlignment.CENTER }

        a.type("Bob\n", 4)

        assertEquals(listOf(BlockKind.HEADING to "Bob", BlockKind.PARAGRAPH to ""), texts)
        assertEquals(TextAlignment.START, blocks[1].align)
    }

    @Test
    fun `enter at the start of a heading pushes it down and keeps it a heading`() {
        val a = block("a", BlockKind.HEADING, "Bob")

        val edit = a.type("\nBob", 1)

        assertEquals(listOf(BlockKind.PARAGRAPH to "", BlockKind.HEADING to "Bob"), texts)
        assertEquals(BlockEdit.Structure(focusId = "a"), edit)
        assertEquals(TextRange(0), a.value.selection)
    }

    @Test
    fun `pasted lines become separate blocks and the caret follows the last one`() {
        val a = block("a", BlockKind.PARAGRAPH, "AB")
        block("z", BlockKind.PARAGRAPH, "Z")

        val edit = a.type("A1\n2\n3B", 6)

        assertEquals(
            listOf(
                BlockKind.PARAGRAPH to "A1",
                BlockKind.PARAGRAPH to "2",
                BlockKind.PARAGRAPH to "3B",
                BlockKind.PARAGRAPH to "Z",
            ),
            texts,
        )
        assertEquals(BlockEdit.Structure(focusId = blocks[2].id), edit)
        assertEquals(TextRange(1), blocks[2].value.selection)
    }

    @Test
    fun `old line breaks in a paragraph do not split it again`() {
        val a = block("a", BlockKind.PARAGRAPH, "a\nb")

        assertEquals(BlockEdit.Content("a"), a.type("a\nbc", 4))
        assertEquals(listOf(BlockKind.PARAGRAPH to "a\nbc"), texts)
    }

    @Test
    fun `inserted line breaks are found next to existing ones`() {
        assertEquals(listOf(1), BlockEditing.insertedLineBreaks("a\nb", "a\n\nb", 2))
        assertEquals(listOf(2), BlockEditing.insertedLineBreaks("a\nb", "a\n\nb", 3))
        assertTrue(BlockEditing.insertedLineBreaks("a\nb", "ab", 1).isEmpty())
    }

    @Test
    fun `backspace at the start joins a paragraph to the text before it, keeping spans`() {
        val italic = SpanStyleData(italic = true)
        block("a", BlockKind.HEADING, "Bob")
        val b = block("b", BlockKind.PARAGRAPH, rich = RichText("matn", listOf(StyleSpan(0, 4, italic))))

        val edit = editing.backspaceAtStart(b)

        assertEquals(BlockEdit.Structure(focusId = "a", removedIds = listOf("b")), edit)
        assertEquals(listOf(BlockKind.HEADING to "Bobmatn"), texts)
        assertEquals(listOf(StyleSpan(3, 7, italic)), blocks[0].rich.spans)
        assertEquals(TextRange(3), blocks[0].value.selection)
    }

    @Test
    fun `backspace at the start of a list item outdents, then turns it into a paragraph`() {
        block("a", BlockKind.PARAGRAPH, "x")
        val b = block("b", BlockKind.BULLET, "item").apply { indent = 1 }

        assertEquals(BlockEdit.Content("b"), editing.backspaceAtStart(b))
        assertEquals(0, b.indent)
        editing.backspaceAtStart(b)
        assertEquals(listOf(BlockKind.PARAGRAPH to "x", BlockKind.PARAGRAPH to "item"), texts)
    }

    @Test
    fun `backspace at the start of a quote or heading turns it into a paragraph`() {
        val a = block("a", BlockKind.QUOTE, "q")
        val b = block("b", BlockKind.HEADING, "h")

        editing.backspaceAtStart(a)
        editing.backspaceAtStart(b)

        assertEquals(listOf(BlockKind.PARAGRAPH to "q", BlockKind.PARAGRAPH to "h"), texts)
    }

    @Test
    fun `backspace in the first paragraph does nothing`() {
        val a = block("a", BlockKind.PARAGRAPH, "x")

        assertEquals(null, editing.backspaceAtStart(a))
    }

    @Test
    fun `backspace after a divider deletes the divider`() {
        block("d", BlockKind.DIVIDER)
        val b = block("b", BlockKind.PARAGRAPH, "x")

        assertEquals(BlockEdit.Structure(focusId = "b", removedIds = listOf("d")), editing.backspaceAtStart(b))
        assertEquals(listOf(BlockKind.PARAGRAPH to "x"), texts)
    }

    @Test
    fun `backspace after a picture selects it and drops an empty paragraph`() {
        block("i", BlockKind.IMAGE)
        val b = block("b", BlockKind.PARAGRAPH, "x")
        val c = block("c", BlockKind.PARAGRAPH)
        blocks.remove(b)

        assertEquals(
            BlockEdit.Structure(focusId = null, removedIds = listOf("c"), selectId = "i"),
            editing.backspaceAtStart(c),
        )
        blocks.add(b)
        assertEquals(BlockEdit.Structure(focusId = null, selectId = "i"), editing.backspaceAtStart(b))
        assertEquals(2, blocks.size)
    }

    @Test
    fun `delete at the end pulls the next block up`() {
        val a = block("a", BlockKind.PARAGRAPH, "ab")
        block("b", BlockKind.BULLET, "cd")

        assertEquals(BlockEdit.Structure(focusId = "a", removedIds = listOf("b")), editing.deleteAtEnd(a))
        assertEquals(listOf(BlockKind.PARAGRAPH to "abcd"), texts)
        assertEquals(TextRange(2), a.value.selection)
    }

    @Test
    fun `markdown markers turn a paragraph into headings, lists and quotes`() {
        val cases = listOf(
            "#" to (BlockKind.HEADING to 1),
            "##" to (BlockKind.HEADING to 2),
            "###" to (BlockKind.HEADING to 3),
            "-" to (BlockKind.BULLET to 1),
            "*" to (BlockKind.BULLET to 1),
            "1." to (BlockKind.NUMBERED to 1),
            ">" to (BlockKind.QUOTE to 1),
        )
        for ((marker, expected) in cases) {
            blocks.clear()
            val a = block("a", BlockKind.PARAGRAPH, marker + "Bob")

            assertEquals(BlockEdit.Content("a"), a.type("$marker Bob", marker.length + 1))
            assertEquals(marker, expected.first, a.kind)
            assertEquals(marker, expected.second, a.headingLevel)
            assertEquals(marker, "Bob", a.rich.text)
            assertEquals(marker, TextRange(0), a.value.selection)
        }
    }

    @Test
    fun `markers only work in paragraphs`() {
        val a = block("a", BlockKind.QUOTE, "#")

        a.type("# ", 2)

        assertEquals(listOf(BlockKind.QUOTE to "# "), texts)
    }

    @Test
    fun `three dashes become a divider followed by a paragraph`() {
        val a = block("a", BlockKind.PARAGRAPH, "--")

        val edit = a.type("---", 3)

        assertEquals(listOf(BlockKind.DIVIDER to "", BlockKind.PARAGRAPH to ""), texts)
        assertEquals(BlockEdit.Structure(focusId = blocks[1].id), edit)
    }

    @Test
    fun `a divider reuses an empty paragraph after it`() {
        val a = block("a", BlockKind.PARAGRAPH, "--")
        block("b", BlockKind.PARAGRAPH)

        assertEquals(BlockEdit.Structure(focusId = "b"), a.type("---", 3))
        assertEquals(2, blocks.size)
    }

    @Test
    fun `a command typed into an empty block converts it`() {
        val a = block("a", BlockKind.PARAGRAPH, "/h")

        assertEquals(BlockEdit.Structure(focusId = "a"), editing.insert(a, 0, 2, BlockKind.HEADING, 2))
        assertEquals(listOf(BlockKind.HEADING to ""), texts)
        assertEquals(2, a.headingLevel)
    }

    @Test
    fun `a command typed after text adds the block below and keeps the text`() {
        val a = block("a", BlockKind.PARAGRAPH, "Matn /ro")

        val edit = editing.insert(a, 5, 8, BlockKind.NUMBERED)

        assertEquals(listOf(BlockKind.PARAGRAPH to "Matn ", BlockKind.NUMBERED to ""), texts)
        assertEquals(BlockEdit.Structure(focusId = blocks[1].id), edit)
    }

    @Test
    fun `a table command makes a table with a header, and continues in a paragraph`() {
        val a = block("a", BlockKind.PARAGRAPH, "/jad")

        val edit = editing.insert(a, 0, 4, BlockKind.TABLE)

        assertEquals(listOf(BlockKind.TABLE to "", BlockKind.PARAGRAPH to ""), texts)
        assertTrue(a.table!!.headerRow)
        assertEquals(3, a.table!!.grid.rowCount)
        assertEquals(BlockEdit.Structure(focusId = blocks[1].id), edit)
    }

    @Test
    fun `a table of the picked size goes below text`() {
        val a = block("a", BlockKind.PARAGRAPH, "Matn /t")

        editing.insert(a, 5, 7, BlockKind.TABLE, table = TableState.create(2, 4, headerRow = false))

        assertEquals(listOf(BlockKind.PARAGRAPH to "Matn ", BlockKind.TABLE to "", BlockKind.PARAGRAPH to ""), texts)
        val table = blocks[1].table!!
        assertEquals(2 to 4, table.grid.rowCount to table.grid.columnCount)
        assertFalse(table.headerRow)
    }

    @Test
    fun `a page break after text goes below it`() {
        val a = block("a", BlockKind.PARAGRAPH, "Matn /p")
        block("b", BlockKind.PARAGRAPH)

        assertEquals(BlockEdit.Structure(focusId = "b"), editing.insert(a, 5, 7, BlockKind.PAGE_BREAK))
        assertEquals(listOf(BlockKind.PARAGRAPH to "Matn ", BlockKind.PAGE_BREAK to "", BlockKind.PARAGRAPH to ""), texts)
    }
}
