package dilnoza.books.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextAnnotationsTest {

    private val red = TextPalette.text.first()
    private val yellow = TextPalette.highlight.first()

    @Test
    fun `plain text has no styles`() {
        val annotated = RichText("Salom").toAnnotatedString()

        assertEquals("Salom", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `every attribute is converted over its range`() {
        val style = SpanStyleData(
            bold = true,
            italic = true,
            underline = true,
            strikethrough = true,
            color = red.light,
            background = yellow.light,
            fontSizeSp = 22,
            fontFamily = "monospace",
        )
        val range = RichText("Salom dunyo", listOf(StyleSpan(6, 11, style))).toAnnotatedString().spanStyles.single()

        assertEquals(6, range.start)
        assertEquals(11, range.end)
        with(range.item) {
            assertEquals(FontWeight.Bold, fontWeight)
            assertEquals(FontStyle.Italic, fontStyle)
            assertEquals(TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)), textDecoration)
            assertEquals(Color(red.light), color)
            assertEquals(Color(yellow.light), background)
            assertEquals(22.sp, fontSize)
            assertEquals(FontFamily.Monospace, fontFamily)
        }
    }

    @Test
    fun `unset attributes stay unspecified so the block style shows through`() {
        val span = SpanStyleData(italic = true).toSpanStyle()

        assertNull(span.fontWeight)
        assertNull(span.textDecoration)
        assertNull(span.fontFamily)
        assertEquals(Color.Unspecified, span.color)
        assertEquals(Color.Unspecified, span.background)
        assertEquals(TextUnit.Unspecified, span.fontSize)
    }

    @Test
    fun `palette colours switch to their dark variant in dark theme`() {
        val span = SpanStyleData(color = red.light, background = yellow.light).toSpanStyle(darkTheme = true)

        assertEquals(Color(red.dark), span.color)
        assertEquals(Color(yellow.dark), span.background)
    }

    @Test
    fun `colours outside the palette are drawn as stored`() {
        val custom = 0xFF123456.toInt()

        assertEquals(Color(custom), SpanStyleData(color = custom).toSpanStyle(darkTheme = true).color)
    }

    @Test
    fun `transformation styles matching text without moving offsets`() {
        val rich = RichText("ab", listOf(StyleSpan(0, 1, SpanStyleData(bold = true))))
        val transformed = RichTextTransformation(rich, darkTheme = false).filter(AnnotatedString("ab"))

        assertEquals("ab", transformed.text.text)
        assertEquals(1, transformed.text.spanStyles.size)
        assertSame(OffsetMapping.Identity, transformed.offsetMapping)
    }

    @Test
    fun `transformation draws plain text while the model lags behind the field`() {
        val rich = RichText("ab", listOf(StyleSpan(0, 1, SpanStyleData(bold = true))))
        val field = AnnotatedString("abc")
        val transformed = RichTextTransformation(rich, darkTheme = false).filter(field)

        assertSame(field, transformed.text)
    }

    @Test
    fun `transformations with the same text and theme are equal`() {
        val rich = RichText("ab", listOf(StyleSpan(0, 1, SpanStyleData(bold = true))))

        assertEquals(RichTextTransformation(rich, false), RichTextTransformation(rich.copy(), false))
    }
}
