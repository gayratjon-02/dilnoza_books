package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal val BOLD = SpanStyleData(bold = true)
internal val ITALIC = SpanStyleData(italic = true)
internal val BOLD_ITALIC = SpanStyleData(bold = true, italic = true)
internal const val RED = 0xFFFF0000.toInt()
internal const val BLUE = 0xFF0000FF.toInt()
internal const val GRINNING = "😀"

class RichTextNormalizeTest {

    @Test
    fun `default style is detected`() {
        assertTrue(SpanStyleData().isDefault)
        assertFalse(BOLD.isDefault)
        assertFalse(SpanStyleData(color = RED).isDefault)
        assertFalse(SpanStyleData(fontFamily = "serif").isDefault)
    }

    @Test
    fun `plain constructor stays source compatible`() {
        val text = RichText("abc")
        assertEquals(emptyList<StyleSpan>(), text.spans)
        assertEquals(RichText(""), RichText.Empty)
    }

    @Test
    fun `canonical text is returned as is`() {
        val text = RichText("hello world", listOf(StyleSpan(0, 5, BOLD), StyleSpan(6, 11, ITALIC)))
        assertSame(text, text.normalized())
    }

    @Test
    fun `empty text drops every span`() {
        val text = RichText("", listOf(StyleSpan(0, 0, BOLD), StyleSpan(0, 3, ITALIC)))
        assertEquals(RichText.Empty, text.normalized())
    }

    @Test
    fun `spans are clamped to the text`() {
        val text = RichText("abc", listOf(StyleSpan(-4, 2, BOLD), StyleSpan(2, 99, ITALIC)))
        assertEquals(listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 3, ITALIC)), text.normalized().spans)
    }

    @Test
    fun `empty inverted and out of range spans are dropped`() {
        val text = RichText("abc", listOf(StyleSpan(1, 1, BOLD), StyleSpan(2, 1, BOLD), StyleSpan(5, 8, BOLD)))
        assertEquals(emptyList<StyleSpan>(), text.normalized().spans)
    }

    @Test
    fun `default style spans are dropped`() {
        val text = RichText("abc", listOf(StyleSpan(0, 3, SpanStyleData())))
        assertEquals(emptyList<StyleSpan>(), text.normalized().spans)
    }

    @Test
    fun `adjacent equal spans are merged`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 4, BOLD), StyleSpan(4, 6, BOLD)))
        assertEquals(listOf(StyleSpan(0, 6, BOLD)), text.normalized().spans)
    }

    @Test
    fun `adjacent different spans are kept apart`() {
        val text = RichText("abcd", listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 4, ITALIC)))
        assertEquals(text.spans, text.normalized().spans)
    }

    @Test
    fun `unsorted spans are sorted`() {
        val text = RichText("abcdef", listOf(StyleSpan(4, 6, ITALIC), StyleSpan(0, 2, BOLD)))
        assertEquals(listOf(StyleSpan(0, 2, BOLD), StyleSpan(4, 6, ITALIC)), text.normalized().spans)
    }

    @Test
    fun `overlapping spans combine their attributes`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 4, BOLD), StyleSpan(2, 6, ITALIC)))
        assertEquals(
            listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 4, BOLD_ITALIC), StyleSpan(4, 6, ITALIC)),
            text.normalized().spans,
        )
    }

    @Test
    fun `later span wins for the attributes it sets`() {
        val text = RichText(
            "abcd",
            listOf(StyleSpan(0, 4, SpanStyleData(color = RED, fontSizeSp = 20)), StyleSpan(1, 3, SpanStyleData(color = BLUE))),
        )
        assertEquals(
            listOf(
                StyleSpan(0, 1, SpanStyleData(color = RED, fontSizeSp = 20)),
                StyleSpan(1, 3, SpanStyleData(color = BLUE, fontSizeSp = 20)),
                StyleSpan(3, 4, SpanStyleData(color = RED, fontSizeSp = 20)),
            ),
            text.normalized().spans,
        )
    }

    @Test
    fun `identical overlapping spans collapse into one`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 4, BOLD), StyleSpan(2, 6, BOLD), StyleSpan(1, 3, BOLD)))
        assertEquals(listOf(StyleSpan(0, 6, BOLD)), text.normalized().spans)
    }

    @Test
    fun `nested span splits the outer one`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 6, BOLD), StyleSpan(2, 3, ITALIC)))
        assertEquals(
            listOf(StyleSpan(0, 2, BOLD), StyleSpan(2, 3, BOLD_ITALIC), StyleSpan(3, 6, BOLD)),
            text.normalized().spans,
        )
    }

    @Test
    fun `spans never cut a surrogate pair`() {
        val text = RichText("a${GRINNING}b", listOf(StyleSpan(2, 4, BOLD), StyleSpan(0, 2, ITALIC)))
        // Both spans widen over the emoji at [1, 3), so it carries both styles.
        assertEquals(
            listOf(StyleSpan(0, 1, ITALIC), StyleSpan(1, 3, BOLD_ITALIC), StyleSpan(3, 4, BOLD)),
            text.normalized().spans,
        )
    }

    @Test
    fun `normalizing twice is stable`() {
        val text = RichText("abcdefgh", listOf(StyleSpan(5, 8, ITALIC), StyleSpan(0, 6, BOLD), StyleSpan(7, 8, ITALIC)))
        val once = text.normalized()
        assertSame(once, once.normalized())
    }

    @Test
    fun `styleAt finds the run of a character`() {
        val text = RichText("abcdef", listOf(StyleSpan(1, 3, BOLD), StyleSpan(4, 6, ITALIC)))
        assertEquals(SpanStyleData.Default, text.styleAt(0))
        assertEquals(BOLD, text.styleAt(1))
        assertEquals(BOLD, text.styleAt(2))
        assertEquals(SpanStyleData.Default, text.styleAt(3))
        assertEquals(ITALIC, text.styleAt(4))
        assertEquals(ITALIC, text.styleAt(5))
    }

    @Test
    fun `styleAt outside the text is default`() {
        val text = RichText("ab", listOf(StyleSpan(0, 2, BOLD)))
        assertEquals(SpanStyleData.Default, text.styleAt(-1))
        assertEquals(SpanStyleData.Default, text.styleAt(2))
        assertEquals(SpanStyleData.Default, RichText.Empty.styleAt(0))
    }

    @Test
    fun `styleAt works on non canonical input`() {
        val text = RichText("abcd", listOf(StyleSpan(2, 4, ITALIC), StyleSpan(0, 3, BOLD)))
        assertEquals(BOLD_ITALIC, text.styleAt(2))
    }

    @Test
    fun `styleAt on both halves of an emoji`() {
        val text = RichText("a$GRINNING", listOf(StyleSpan(1, 3, BOLD)))
        assertEquals(BOLD, text.styleAt(1))
        assertEquals(BOLD, text.styleAt(2))
    }

    @Test
    fun `typing style comes from the previous character or the first one at the start`() {
        val text = RichText("abcd", listOf(StyleSpan(0, 1, ITALIC), StyleSpan(2, 4, BOLD)))
        assertEquals(ITALIC, text.typingStyleAt(0))
        assertEquals(ITALIC, text.typingStyleAt(1))
        assertEquals(SpanStyleData.Default, text.typingStyleAt(2))
        assertEquals(BOLD, text.typingStyleAt(4))
        assertEquals(BOLD, text.typingStyleAt(40))
        assertEquals(SpanStyleData.Default, RichText.Empty.typingStyleAt(0))
    }

    @Test
    fun `isApplied requires every character`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 3, BOLD), StyleSpan(3, 5, BOLD_ITALIC)))
        assertTrue(text.isApplied(0, 5, StyleAttribute.BOLD))
        assertFalse(text.isApplied(0, 6, StyleAttribute.BOLD))
        assertFalse(text.isApplied(2, 4, StyleAttribute.ITALIC))
        assertTrue(text.isApplied(3, 5, StyleAttribute.ITALIC))
        assertFalse(text.isApplied(0, 6, StyleAttribute.UNDERLINE))
    }

    @Test
    fun `isApplied detects gaps between runs`() {
        val text = RichText("abcdef", listOf(StyleSpan(0, 2, BOLD), StyleSpan(3, 6, BOLD)))
        assertFalse(text.isApplied(0, 6, StyleAttribute.BOLD))
        assertTrue(text.isApplied(3, 6, StyleAttribute.BOLD))
    }

    @Test
    fun `isApplied accepts a reversed range`() {
        val text = RichText("abc", listOf(StyleSpan(0, 3, BOLD)))
        assertTrue(text.isApplied(3, 0, StyleAttribute.BOLD))
    }

    @Test
    fun `isApplied on an empty range uses the typing style`() {
        val text = RichText("abcd", listOf(StyleSpan(0, 2, BOLD)))
        assertTrue(text.isApplied(0, 0, StyleAttribute.BOLD))
        assertTrue(text.isApplied(2, 2, StyleAttribute.BOLD))
        assertFalse(text.isApplied(3, 3, StyleAttribute.BOLD))
        assertFalse(RichText.Empty.isApplied(0, 0, StyleAttribute.BOLD))
    }

    @Test
    fun `isApplied treats a half selected emoji as whole`() {
        val text = RichText("${GRINNING}x", listOf(StyleSpan(0, 2, BOLD)))
        assertTrue(text.isApplied(1, 2, StyleAttribute.BOLD))
        assertFalse(text.isApplied(1, 3, StyleAttribute.BOLD))
    }

    @Test
    fun `attribute helpers read and write each flag`() {
        for (attribute in StyleAttribute.entries) {
            val on = SpanStyleData().with(attribute, true)
            assertTrue(on.has(attribute))
            assertEquals(1, StyleAttribute.entries.count { on.has(it) })
            assertTrue(on.with(attribute, false).isDefault)
        }
    }

    @Test
    fun `overlay keeps base values the top leaves unset`() {
        val base = SpanStyleData(bold = true, color = RED, fontFamily = "serif")
        val top = SpanStyleData(italic = true, color = BLUE, background = RED, fontSizeSp = 18)
        assertEquals(
            SpanStyleData(bold = true, italic = true, color = BLUE, background = RED, fontSizeSp = 18, fontFamily = "serif"),
            base.overlay(top),
        )
    }
}
