package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextSplitTest {

    private val sample = RichText(
        "Hello bold world",
        listOf(StyleSpan(0, 5, ITALIC), StyleSpan(6, 10, BOLD), StyleSpan(11, 16, BOLD_ITALIC)),
    )

    @Test
    fun `subText clips and shifts spans`() {
        assertEquals(
            RichText("lo bold w", listOf(StyleSpan(0, 2, ITALIC), StyleSpan(3, 7, BOLD), StyleSpan(8, 9, BOLD_ITALIC))),
            sample.subText(3, 12),
        )
    }

    @Test
    fun `subText inside one run`() {
        assertEquals(RichText("ol", listOf(StyleSpan(0, 2, BOLD))), sample.subText(7, 9))
    }

    @Test
    fun `subText of plain gap has no spans`() {
        assertEquals(RichText(" "), sample.subText(5, 6))
    }

    @Test
    fun `subText of the whole text is the text`() {
        assertEquals(sample, sample.subText(0, sample.text.length))
        assertEquals(sample, sample.subText(-3, 99))
    }

    @Test
    fun `subText with empty or reversed range is empty`() {
        assertEquals(RichText.Empty, sample.subText(4, 4))
        assertEquals(RichText.Empty, sample.subText(8, 2))
        assertEquals(RichText.Empty, RichText.Empty.subText(0, 0))
    }

    @Test
    fun `subText never cuts an emoji`() {
        val text = RichText("a${GRINNING}b", listOf(StyleSpan(1, 3, BOLD)))
        assertEquals(RichText("a"), text.subText(0, 2))
        assertEquals(RichText("${GRINNING}b", listOf(StyleSpan(0, 2, BOLD))), text.subText(2, 4))
    }

    @Test
    fun `split inside a run divides it`() {
        val (left, right) = sample.split(8)
        assertEquals(RichText("Hello bo", listOf(StyleSpan(0, 5, ITALIC), StyleSpan(6, 8, BOLD))), left)
        assertEquals(RichText("ld world", listOf(StyleSpan(0, 2, BOLD), StyleSpan(3, 8, BOLD_ITALIC))), right)
    }

    @Test
    fun `split at the edges`() {
        assertEquals(RichText.Empty to sample, sample.split(0))
        assertEquals(sample to RichText.Empty, sample.split(sample.text.length))
        assertEquals(RichText.Empty to sample, sample.split(-1))
        assertEquals(sample to RichText.Empty, sample.split(100))
        assertEquals(RichText.Empty to RichText.Empty, RichText.Empty.split(0))
    }

    @Test
    fun `split inside an emoji moves before it`() {
        val text = RichText("a$GRINNING", listOf(StyleSpan(1, 3, BOLD)))
        val (left, right) = text.split(2)
        assertEquals(RichText("a"), left)
        assertEquals(RichText(GRINNING, listOf(StyleSpan(0, 2, BOLD))), right)
    }

    @Test
    fun `split then plus round trips at every index`() {
        val text = RichText(
            "ab${GRINNING}cd\nef",
            listOf(StyleSpan(0, 3, BOLD), StyleSpan(4, 6, ITALIC), StyleSpan(7, 9, BOLD_ITALIC)),
        ).normalized()
        for (i in -1..text.text.length + 1) {
            val (left, right) = text.split(i)
            assertEquals("index $i", text, left + right)
        }
    }

    @Test
    fun `plus merges runs with equal style at the seam`() {
        val left = RichText("ab", listOf(StyleSpan(0, 2, BOLD)))
        val right = RichText("cd", listOf(StyleSpan(0, 1, BOLD)))
        assertEquals(RichText("abcd", listOf(StyleSpan(0, 3, BOLD))), left + right)
    }

    @Test
    fun `plus keeps different styles apart and shifts spans`() {
        val left = RichText("ab", listOf(StyleSpan(1, 2, BOLD)))
        val right = RichText("cde", listOf(StyleSpan(1, 3, ITALIC)))
        assertEquals(RichText("abcde", listOf(StyleSpan(1, 2, BOLD), StyleSpan(3, 5, ITALIC))), left + right)
    }

    @Test
    fun `plus with empty sides`() {
        assertEquals(sample, sample + RichText.Empty)
        assertEquals(sample, RichText.Empty + sample)
        assertEquals(RichText.Empty, RichText.Empty + RichText.Empty)
    }

    @Test
    fun `plus normalizes its inputs`() {
        val left = RichText("ab", listOf(StyleSpan(0, 9, BOLD)))
        val right = RichText("cd", listOf(StyleSpan(1, 2, BOLD), StyleSpan(0, 1, BOLD)))
        assertEquals(RichText("abcd", listOf(StyleSpan(0, 4, BOLD))), left + right)
    }
}
