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

    @Test
    fun `transformation hides the sentinel and maps offsets around it`() {
        val rich = RichText("ab", listOf(StyleSpan(0, 1, SpanStyleData(bold = true))))
        val transformed = RichTextTransformation(rich, darkTheme = false, hiddenPrefix = 1).filter(AnnotatedString("\nab"))

        assertEquals("ab", transformed.text.text)
        assertEquals(1, transformed.text.spanStyles.size)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(2, transformed.offsetMapping.originalToTransformed(3))
        assertEquals(1, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(2))
    }

    @Test
    fun `block offsets map past the page gaps drawn before them`() {
        val text = "Birinchi qator. Ikkinchi qator. Uchinchi qator."
        val gaps = listOf(PageGap(16, 120f), PageGap(32, 120f))

        assertEquals(0, drawnOffset(0, gaps, text.length))
        assertEquals(15, drawnOffset(15, gaps, text.length))
        // A character starting the line after a gap is drawn after the gap's character.
        assertEquals(17, drawnOffset(16, gaps, text.length))
        assertEquals(20, drawnOffset(19, gaps, text.length))
        assertEquals(34, drawnOffset(32, gaps, text.length))
        assertEquals(text.length + 2, drawnOffset(text.length, gaps, text.length))
        // Gaps outside the text are not drawn.
        assertEquals(5, drawnOffset(5, listOf(PageGap(0, 1f), PageGap(99, 1f)), text.length))
    }

    @Test
    fun `drawn offsets agree with the field transformation`() {
        val text = "Birinchi qator. Ikkinchi qator. Uchinchi qator."
        val gaps = listOf(PageGap(32, 120f), PageGap(16, 120f), PageGap(16, 120f))
        val transformed = RichTextTransformation(RichText(text), darkTheme = false, hiddenPrefix = 1, gaps = gaps)
            .filter(AnnotatedString(FIELD_SENTINEL + text))

        for (offset in 0..text.length) {
            val drawn = drawnOffset(offset, gaps, text.length)
            assertEquals("offset $offset", transformed.offsetMapping.originalToTransformed(offset + 1), drawn)
            if (offset < text.length) assertEquals(text[offset], transformed.text.text[drawn])
        }
    }
}
