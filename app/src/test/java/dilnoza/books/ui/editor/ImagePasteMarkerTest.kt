package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagePasteMarkerTest {

    @Test
    fun `typed text has no pasted picture`() {
        assertNull(TextFieldValue("\nAlfa Beta", TextRange(5)).withoutImagePasteMarker())
    }

    @Test
    fun `pasted picture marker is taken out and the caret stays where it was pasted`() {
        val pasted = TextFieldValue("\nAl${IMAGE_PASTE_MARKER}fa", TextRange(4))

        assertEquals(TextFieldValue("\nAlfa", TextRange(3)), pasted.withoutImagePasteMarker())
    }

    @Test
    fun `picture pasted over a selection keeps the rest of the text`() {
        // "Alfa Beta" with "Beta" selected, replaced by the marker.
        val pasted = TextFieldValue("\nAlfa $IMAGE_PASTE_MARKER", TextRange(7))

        assertEquals(TextFieldValue("\nAlfa ", TextRange(6)), pasted.withoutImagePasteMarker())
    }
}
