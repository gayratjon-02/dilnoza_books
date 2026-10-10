package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.domain.model.TextAlignment
import org.junit.Assert.assertEquals
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
}
