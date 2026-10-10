package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextStyleTest {

    private val plain = RichText("hello world")

    @Test
    fun `applyStyle on plain text adds one run`() {
        val result = plain.applyStyle(0, 5) { it.copy(bold = true) }
        assertEquals(listOf(StyleSpan(0, 5, BOLD)), result.spans)
        assertEquals(plain.text, result.text)
    }

    @Test
    fun `applyStyle inside a run splits it in three`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 6, BOLD)))
        val result = text.applyStyle(2, 4) { it.copy(italic = true) }
        assertEquals(
            listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 4, BOLD_ITALIC), StyleSpan(4, 6, BOLD)),
            result.spans,
        )
    }

    @Test
    fun `applyStyle across runs and gaps`() {
        val text = RichText("abcdefgh", listOf(StyleSpan(1, 3, BOLD), StyleSpan(5, 7, ITALIC)))
        val result = text.applyStyle(0, 8) { it.copy(underline = true) }
        val u = SpanStyleData(underline = true)
        assertEquals(
            listOf(
                StyleSpan(0, 1, u),
                StyleSpan(1, 3, BOLD.copy(underline = true)),
                StyleSpan(3, 5, u),
                StyleSpan(5, 7, ITALIC.copy(underline = true)),
                StyleSpan(7, 8, u),
            ),
            result.spans,
        )
    }

    @Test
    fun `applyStyle leaves runs outside the range untouched`() {
        val text = RichText("abcdefgh", listOf(StyleSpan(0, 2, ITALIC), StyleSpan(6, 8, ITALIC)))
        val result = text.applyStyle(3, 5) { it.copy(bold = true) }
        assertEquals(listOf(StyleSpan(0, 2, ITALIC), StyleSpan(3, 5, BOLD), StyleSpan(6, 8, ITALIC)), result.spans)
    }

    @Test
    fun `applyStyle merges with equal neighbours`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 2, BOLD), StyleSpan(4, 6, BOLD)))
        assertEquals(listOf(StyleSpan(0, 6, BOLD)), text.applyStyle(2, 4) { it.copy(bold = true) }.spans)
    }

    @Test
    fun `applyStyle with empty or reversed range`() {
        val text = RichText("abc", listOf(StyleSpan(0, 1, BOLD)))
        assertEquals(text, text.applyStyle(1, 1) { BOLD_ITALIC })
        assertEquals(listOf(StyleSpan(0, 2, BOLD)), text.applyStyle(2, 0) { it.copy(bold = true) }.spans)
    }

    @Test
    fun `applyStyle clamps the range`() {
        assertEquals(listOf(StyleSpan(0, 11, BOLD)), plain.applyStyle(-5, 50) { it.copy(bold = true) }.spans)
        assertEquals(plain, plain.applyStyle(20, 30) { BOLD })
    }

    @Test
    fun `applyStyle on empty text is a no op`() {
        assertEquals(RichText.Empty, RichText.Empty.applyStyle(0, 0) { BOLD })
    }

    @Test
    fun `applyStyle widens over a half selected emoji`() {
        val text = RichText("a${GRINNING}b")
        assertEquals(listOf(StyleSpan(1, 3, BOLD)), text.applyStyle(2, 3) { it.copy(bold = true) }.spans)
        assertEquals(listOf(StyleSpan(0, 3, BOLD)), text.applyStyle(0, 2) { it.copy(bold = true) }.spans)
    }

    @Test
    fun `toggle on plain text sets the attribute`() {
        assertEquals(listOf(StyleSpan(0, 5, BOLD)), plain.toggle(0, 5, StyleAttribute.BOLD).spans)
    }

    @Test
    fun `toggle on fully styled range removes it`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 6, BOLD_ITALIC)))
        assertEquals(
            listOf(StyleSpan(0, 2, BOLD_ITALIC), StyleSpan(2, 4, ITALIC), StyleSpan(4, 6, BOLD_ITALIC)),
            text.toggle(2, 4, StyleAttribute.BOLD).spans,
        )
    }

    @Test
    fun `toggle on partially styled range sets it everywhere`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 3, BOLD)))
        assertEquals(listOf(StyleSpan(0, 6, BOLD)), text.toggle(1, 6, StyleAttribute.BOLD).spans)
    }

    @Test
    fun `toggle twice restores an attribute absent from the range`() {
        val text = RichText("abcdef", listOf(StyleSpan(1, 4, ITALIC)))
        for (attribute in StyleAttribute.entries - StyleAttribute.ITALIC) {
            assertEquals(text, text.toggle(0, 5, attribute).toggle(0, 5, attribute))
        }
    }

    @Test
    fun `toggle twice over a partial attribute clears it`() {
        val text = RichText("abcdef", listOf(StyleSpan(1, 4, ITALIC)))
        val once = text.toggle(0, 5, StyleAttribute.ITALIC)
        assertEquals(listOf(StyleSpan(0, 5, ITALIC)), once.spans)
        assertEquals(RichText("abcdef"), once.toggle(0, 5, StyleAttribute.ITALIC))
    }

    @Test
    fun `toggle removing the last attribute drops the run`() {
        val text = RichText("abc", listOf(StyleSpan(0, 3, BOLD)))
        assertEquals(RichText("abc"), text.toggle(0, 3, StyleAttribute.BOLD))
    }

    @Test
    fun `toggle each attribute maps to its flag`() {
        assertEquals(SpanStyleData(italic = true), plain.toggle(0, 1, StyleAttribute.ITALIC).styleAt(0))
        assertEquals(SpanStyleData(underline = true), plain.toggle(0, 1, StyleAttribute.UNDERLINE).styleAt(0))
        assertEquals(SpanStyleData(strikethrough = true), plain.toggle(0, 1, StyleAttribute.STRIKETHROUGH).styleAt(0))
    }

    @Test
    fun `toggle with empty range changes nothing`() {
        val text = RichText("abc", listOf(StyleSpan(0, 3, BOLD)))
        assertEquals(text, text.toggle(1, 1, StyleAttribute.BOLD))
    }

    @Test
    fun `setColor sets and clears`() {
        val colored = plain.setColor(0, 5, RED)
        assertEquals(listOf(StyleSpan(0, 5, SpanStyleData(color = RED))), colored.spans)
        assertEquals(plain, colored.setColor(0, 5, null))
        assertEquals(
            listOf(StyleSpan(0, 2, SpanStyleData(color = RED)), StyleSpan(2, 5, SpanStyleData(color = BLUE))),
            colored.setColor(2, 5, BLUE).spans,
        )
    }

    @Test
    fun `setBackground keeps other attributes`() {
        val text = RichText("abc", listOf(StyleSpan(0, 3, BOLD)))
        assertEquals(
            listOf(StyleSpan(0, 3, BOLD.copy(background = BLUE))),
            text.setBackground(0, 3, BLUE).spans,
        )
    }

    @Test
    fun `setFontSize and setFontFamily`() {
        val sized = plain.setFontSize(6, 11, 24).setFontFamily(0, 11, "serif")
        assertEquals(
            listOf(
                StyleSpan(0, 6, SpanStyleData(fontFamily = "serif")),
                StyleSpan(6, 11, SpanStyleData(fontFamily = "serif", fontSizeSp = 24)),
            ),
            sized.spans,
        )
        assertEquals(listOf(StyleSpan(6, 11, SpanStyleData(fontSizeSp = 24))), sized.setFontFamily(0, 11, null).spans)
    }

    @Test
    fun `clearFormatting removes everything in range`() {
        val text = RichText(
            "abcdef",
            listOf(StyleSpan(0, 3, BOLD), StyleSpan(3, 6, SpanStyleData(italic = true, color = RED))),
        )
        assertEquals(
            listOf(StyleSpan(0, 2, BOLD), StyleSpan(5, 6, SpanStyleData(italic = true, color = RED))),
            text.clearFormatting(2, 5).spans,
        )
        assertEquals(RichText("abcdef"), text.clearFormatting(0, 6))
    }
}
