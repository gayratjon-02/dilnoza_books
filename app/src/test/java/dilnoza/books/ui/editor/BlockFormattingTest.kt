package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.editor.richtext.StyleAttribute
import dilnoza.books.editor.richtext.styleAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockFormattingTest {

    private val bold = SpanStyleData(bold = true)
    private val boldToggle = FormatChange.Toggle(StyleAttribute.BOLD)

    private fun block(rich: RichText, selection: TextRange) = EditorBlock("b", BlockKind.PARAGRAPH, rich, selection)

    @Test
    fun `selection is styled and stays selected`() {
        val block = block(RichText("Salom dunyo"), TextRange(6, 11))

        val pending = block.applyFormat(boldToggle, null)

        assertNull(pending)
        assertEquals(listOf(StyleSpan(6, 11, bold)), block.rich.spans)
        assertEquals(TextRange(6, 11), block.value.selection)
        assertTrue(block.formatState(null).bold)
    }

    @Test
    fun `reversed selection is styled too`() {
        val block = block(RichText("Salom dunyo"), TextRange(5, 0))

        block.applyFormat(FormatChange.TextColor(0xFFC62828.toInt()), null)

        assertEquals(listOf(StyleSpan(0, 5, SpanStyleData(color = 0xFFC62828.toInt()))), block.rich.spans)
    }

    @Test
    fun `toggling a fully bold selection removes bold`() {
        val block = block(RichText("Salom", listOf(StyleSpan(0, 5, bold))), TextRange(0, 5))

        block.applyFormat(boldToggle, null)

        assertTrue(block.rich.spans.isEmpty())
        assertFalse(block.formatState(null).bold)
    }

    @Test
    fun `collapsed caret sets a pending style and leaves the text alone`() {
        val rich = RichText("Salom ")
        val block = block(rich, TextRange(6))

        val pending = block.applyFormat(boldToggle, null)

        assertEquals(bold, pending)
        assertEquals(rich, block.rich)
        assertTrue(block.formatState(pending).bold)
        assertFalse(block.formatState(null).bold)
    }

    @Test
    fun `pending style is given to typed text`() {
        val block = block(RichText("Salom "), TextRange(6))
        val pending = block.applyFormat(boldToggle, null)

        block.setValue(TextFieldValue("Salom d", TextRange(7)), pending)

        assertEquals(listOf(StyleSpan(6, 7, bold)), block.rich.spans)
    }

    @Test
    fun `typing after a bold word continues bold`() {
        val block = block(RichText("Salom", listOf(StyleSpan(0, 5, bold))), TextRange(5))

        assertTrue(block.formatState(null).bold)
        block.setValue(TextFieldValue("Salomlar", TextRange(8)))

        assertEquals(listOf(StyleSpan(0, 8, bold)), block.rich.spans)
    }

    @Test
    fun `turning bold off at the caret makes following text plain`() {
        val block = block(RichText("Salom", listOf(StyleSpan(0, 5, bold))), TextRange(5))
        val pending = block.applyFormat(boldToggle, null)

        block.setValue(TextFieldValue("Salom dunyo", TextRange(11)), pending)

        assertEquals(listOf(StyleSpan(0, 5, bold)), block.rich.spans)
        assertEquals(SpanStyleData.Default, block.rich.styleAt(8))
    }

    @Test
    fun `pending changes combine`() {
        val block = block(RichText(""), TextRange(0))
        val first = block.applyFormat(boldToggle, null)

        val second = block.applyFormat(FormatChange.FontSize(22), first)

        assertEquals(SpanStyleData(bold = true, fontSizeSp = 22), second)
    }

    @Test
    fun `clear formatting resets the selection`() {
        val styled = SpanStyleData(bold = true, italic = true, color = 0xFF1565C0.toInt(), background = 0xFFFFF59D.toInt())
        val block = block(RichText("Salom", listOf(StyleSpan(0, 5, styled))), TextRange(1, 4))

        block.applyFormat(FormatChange.Clear, null)

        assertEquals(listOf(StyleSpan(0, 1, styled), StyleSpan(4, 5, styled)), block.rich.spans)
    }

    @Test
    fun `restyling keeps the keyboard composing region`() {
        val block = block(RichText("Salom"), TextRange(5))
        block.setValue(TextFieldValue("Salom", TextRange(0, 5), composition = TextRange(0, 5)))

        block.applyFormat(boldToggle, null)

        assertEquals(TextRange(0, 5), block.value.composition)
    }

    @Test
    fun `colour state reports the selection's first character`() {
        val red = 0xFFC62828.toInt()
        val block = block(RichText("ab", listOf(StyleSpan(0, 1, SpanStyleData(color = red)))), TextRange(0, 2))

        val state = block.formatState(null)

        assertEquals(red, state.color)
        assertFalse(state.bold)
    }
}
