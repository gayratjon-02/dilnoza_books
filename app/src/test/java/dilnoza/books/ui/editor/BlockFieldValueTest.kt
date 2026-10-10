package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockFieldValueTest {

    private val current = TextFieldValue("abc", TextRange(0))

    @Test
    fun `field value hides the text behind the sentinel`() {
        val field = TextFieldValue("abc", TextRange(1, 2), composition = TextRange(0, 3)).toFieldValue()

        assertEquals(TextFieldValue("\nabc", TextRange(2, 3), composition = TextRange(1, 4)), field)
    }

    @Test
    fun `typing comes back without the sentinel`() {
        val edit = fieldEdit(current, TextFieldValue("\nxabc", TextRange(2)))

        assertEquals(FieldEdit.Change(TextFieldValue("xabc", TextRange(1))), edit)
    }

    @Test
    fun `a caret placed before the sentinel is kept at the start of the text`() {
        assertEquals(FieldEdit.Change(TextFieldValue("abc", TextRange(0))), fieldEdit(current, TextFieldValue("\nabc", TextRange(0))))
    }

    @Test
    fun `deleting the sentinel is a backspace at the start`() {
        assertEquals(FieldEdit.BackspaceAtStart, fieldEdit(current, TextFieldValue("abc", TextRange(0))))
        assertEquals(FieldEdit.BackspaceAtStart, fieldEdit(TextFieldValue(""), TextFieldValue("")))
    }

    @Test
    fun `replacing text together with the sentinel keeps the new text`() {
        assertEquals(FieldEdit.Change(TextFieldValue("x", TextRange(1))), fieldEdit(current, TextFieldValue("x", TextRange(1))))
    }
}
