package dilnoza.books.editor.richtext

import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextEditorTest {

    // "plain " [0,6) is unstyled, "bold" [6,10) is bold, " tail" [10,15) is italic.
    private val sample = RichText("plain bold tail", listOf(StyleSpan(6, 10, BOLD), StyleSpan(10, 15, ITALIC)))

    private fun change(old: RichText, newText: String, typingStyle: SpanStyleData? = null, cursor: Int? = null) =
        RichTextEditor.applyTextChange(old, newText, typingStyle, cursor)

    @Test
    fun `unchanged text keeps spans`() {
        assertEquals(sample, change(sample, sample.text))
    }

    @Test
    fun `unchanged text is normalized`() {
        val messy = RichText("ab", listOf(StyleSpan(0, 1, BOLD), StyleSpan(1, 2, BOLD)))
        assertEquals(RichText("ab", listOf(StyleSpan(0, 2, BOLD))), change(messy, "ab"))
    }

    @Test
    fun `typing into empty text with and without typing style`() {
        assertEquals(RichText("a"), change(RichText.Empty, "a"))
        assertEquals(RichText("a", listOf(StyleSpan(0, 1, BOLD))), change(RichText.Empty, "a", BOLD))
    }

    @Test
    fun `typing inside a run extends it`() {
        assertEquals(
            RichText("plain boXld tail", listOf(StyleSpan(6, 11, BOLD), StyleSpan(11, 16, ITALIC))),
            change(sample, "plain boXld tail"),
        )
    }

    @Test
    fun `typing at the end of a run inherits it`() {
        // Caret after "bold", before the italic run: the previous character wins.
        assertEquals(
            RichText("plain boldX tail", listOf(StyleSpan(6, 11, BOLD), StyleSpan(11, 16, ITALIC))),
            change(sample, "plain boldX tail"),
        )
    }

    @Test
    fun `typing right before a run stays outside it`() {
        assertEquals(
            RichText("plain Xbold tail", listOf(StyleSpan(7, 11, BOLD), StyleSpan(11, 16, ITALIC))),
            change(sample, "plain Xbold tail"),
        )
    }

    @Test
    fun `typing at the end of the text inherits the last run`() {
        assertEquals(
            RichText("plain bold tail!", listOf(StyleSpan(6, 10, BOLD), StyleSpan(10, 16, ITALIC))),
            change(sample, "plain bold tail!"),
        )
    }

    @Test
    fun `typing at index zero inherits the first character`() {
        val text = RichText("abc", listOf(StyleSpan(0, 2, BOLD)))
        assertEquals(RichText("Xabc", listOf(StyleSpan(0, 3, BOLD))), change(text, "Xabc"))
        assertEquals(RichText("Xplain bold tail", listOf(StyleSpan(7, 11, BOLD), StyleSpan(11, 16, ITALIC))), change(sample, "Xplain bold tail"))
    }

    @Test
    fun `typing style overrides inheritance`() {
        assertEquals(
            RichText("plain boXld tail", listOf(StyleSpan(6, 8, BOLD), StyleSpan(8, 9, ITALIC), StyleSpan(9, 11, BOLD), StyleSpan(11, 16, ITALIC))),
            change(sample, "plain boXld tail", ITALIC),
        )
    }

    @Test
    fun `default typing style inserts plain text inside a run`() {
        assertEquals(
            RichText("plain boXld tail", listOf(StyleSpan(6, 8, BOLD), StyleSpan(9, 11, BOLD), StyleSpan(11, 16, ITALIC))),
            change(sample, "plain boXld tail", SpanStyleData.Default),
        )
    }

    @Test
    fun `typing style is ignored for pure deletion`() {
        assertEquals(
            RichText("plain bld tail", listOf(StyleSpan(6, 9, BOLD), StyleSpan(9, 14, ITALIC))),
            change(sample, "plain bld tail", ITALIC),
        )
    }

    @Test
    fun `deleting inside a run shrinks it`() {
        assertEquals(
            RichText("plain bd tail", listOf(StyleSpan(6, 8, BOLD), StyleSpan(8, 13, ITALIC))),
            change(sample, "plain bd tail"),
        )
    }

    @Test
    fun `deleting a whole run removes it`() {
        assertEquals(RichText("plain  tail", listOf(StyleSpan(6, 11, ITALIC))), change(sample, "plain  tail"))
    }

    @Test
    fun `deleting across runs trims both`() {
        assertEquals(
            RichText("plain boail", listOf(StyleSpan(6, 8, BOLD), StyleSpan(8, 11, ITALIC))),
            change(sample, "plain boail"),
        )
    }

    @Test
    fun `deleting so equal runs meet merges them`() {
        val text = RichText("ab-cd", listOf(StyleSpan(0, 2, BOLD), StyleSpan(3, 5, BOLD)))
        assertEquals(RichText("abcd", listOf(StyleSpan(0, 4, BOLD))), change(text, "abcd"))
    }

    @Test
    fun `deleting everything clears spans`() {
        assertEquals(RichText.Empty, change(sample, ""))
    }

    @Test
    fun `replacing a selection takes the style of its first character`() {
        // Selecting "bold" and typing "B" keeps it bold even though the previous character is plain.
        assertEquals(
            RichText("plain B tail", listOf(StyleSpan(6, 7, BOLD), StyleSpan(7, 12, ITALIC))),
            change(sample, "plain B tail"),
        )
    }

    @Test
    fun `replacing across runs takes the first run style`() {
        assertEquals(
            RichText("plain bXYil", listOf(StyleSpan(6, 9, BOLD), StyleSpan(9, 11, ITALIC))),
            change(sample, "plain bXYil"),
        )
    }

    @Test
    fun `replacing the whole text uses the first character style`() {
        val text = RichText("abc", listOf(StyleSpan(0, 1, ITALIC)))
        assertEquals(RichText("xyz", listOf(StyleSpan(0, 3, ITALIC))), change(text, "xyz"))
        assertEquals(RichText("xyz", listOf(StyleSpan(0, 3, BOLD))), change(text, "xyz", BOLD))
    }

    @Test
    fun `pasting multi line text inside a run`() {
        val pasted = "one\ntwo\n"
        assertEquals(
            RichText("plain bo${pasted}ld tail", listOf(StyleSpan(6, 18, BOLD), StyleSpan(18, 23, ITALIC))),
            change(sample, "plain bo${pasted}ld tail"),
        )
    }

    @Test
    fun `pasting multi line text with a typing style`() {
        val result = change(RichText("ab"), "a\nx\nb", ITALIC)
        assertEquals(RichText("a\nx\nb", listOf(StyleSpan(1, 4, ITALIC))), result)
    }

    @Test
    fun `typing an emoji inside a run`() {
        assertEquals(
            RichText("plain bo${GRINNING}ld tail", listOf(StyleSpan(6, 12, BOLD), StyleSpan(12, 17, ITALIC))),
            change(sample, "plain bo${GRINNING}ld tail"),
        )
    }

    @Test
    fun `deleting an emoji removes both halves from the run`() {
        val text = RichText("a${GRINNING}b", listOf(StyleSpan(1, 3, BOLD), StyleSpan(3, 4, ITALIC)))
        assertEquals(RichText("ab", listOf(StyleSpan(1, 2, ITALIC))), change(text, "ab"))
    }

    @Test
    fun `swapping emoji that share a high surrogate never splits the pair`() {
        val grin = "😀"
        val wink = "😉"
        val text = RichText("x${grin}y", listOf(StyleSpan(1, 3, BOLD)))
        // The common prefix "x\uD83D" would cut the pair; the edit must replace the whole emoji.
        val result = change(text, "x${wink}y", ITALIC)
        assertEquals(RichText("x${wink}y", listOf(StyleSpan(1, 3, ITALIC))), result)
    }

    @Test
    fun `emoji sharing a low surrogate never split the pair`() {
        val a = "😀"
        val b = "🨀"
        val text = RichText("${a}z")
        assertEquals(RichText("${b}z", listOf(StyleSpan(0, 2, BOLD))), change(text, "${b}z", BOLD))
    }

    @Test
    fun `typing after an emoji inherits its style`() {
        val text = RichText(GRINNING, listOf(StyleSpan(0, 2, BOLD)))
        assertEquals(RichText("${GRINNING}a", listOf(StyleSpan(0, 3, BOLD))), change(text, "${GRINNING}a"))
    }

    @Test
    fun `ambiguous insertion without cursor goes to the latest position`() {
        val text = RichText("aa", listOf(StyleSpan(0, 1, BOLD)))
        // "aa" -> "aaa" is read as appending, which inherits the plain second "a".
        assertEquals(RichText("aaa", listOf(StyleSpan(0, 1, BOLD))), change(text, "aaa"))
    }

    @Test
    fun `cursor places an ambiguous insertion`() {
        val text = RichText("aa", listOf(StyleSpan(1, 2, BOLD)))
        // Typed at index 0 (caret now at 1): inherits the first character, which is plain.
        assertEquals(RichText("aaa", listOf(StyleSpan(2, 3, BOLD))), change(text, "aaa", cursor = 1))
        // Typed at index 2 (caret at 3): inherits the bold second character.
        assertEquals(RichText("aaa", listOf(StyleSpan(1, 3, BOLD))), change(text, "aaa", cursor = 3))
    }

    @Test
    fun `cursor places an ambiguous deletion`() {
        val text = RichText("abbc", listOf(StyleSpan(1, 2, BOLD)))
        // Backspace on the bold "b" leaves the caret at 1.
        assertEquals(RichText("abc"), change(text, "abc", cursor = 1))
        // Backspace on the plain "b" leaves the caret at 2.
        assertEquals(RichText("abc", listOf(StyleSpan(1, 2, BOLD))), change(text, "abc", cursor = 2))
    }

    @Test
    fun `out of range cursor is clamped`() {
        val text = RichText("ab", listOf(StyleSpan(0, 2, BOLD)))
        assertEquals(RichText("abc", listOf(StyleSpan(0, 3, BOLD))), change(text, "abc", cursor = 99))
        assertEquals(RichText("cab", listOf(StyleSpan(0, 3, BOLD))), change(text, "cab", cursor = -4))
    }

    @Test
    fun `cursor inside an emoji does not split it`() {
        val text = RichText(GRINNING)
        val result = change(text, "$GRINNING$GRINNING", BOLD, cursor = 3)
        assertEquals("$GRINNING$GRINNING", result.text)
        assertEquals(1, result.spans.size)
        assertEquals(2, result.spans[0].end - result.spans[0].start)
    }

    @Test
    fun `non canonical input is normalized first`() {
        val messy = RichText("abcd", listOf(StyleSpan(2, 9, ITALIC), StyleSpan(0, 3, BOLD)))
        assertEquals(
            RichText("abXcd", listOf(StyleSpan(0, 3, BOLD), StyleSpan(3, 4, BOLD_ITALIC), StyleSpan(4, 5, ITALIC))),
            change(messy, "abXcd"),
        )
    }

    @Test
    fun `sequence of keystrokes builds styled text`() {
        var text = RichText.Empty
        for (word in listOf("H", "He", "Hel", "Hell", "Hello")) text = change(text, word, BOLD)
        text = change(text, "Hello ", SpanStyleData.Default)
        text = change(text, "Hello w")
        text = change(text, "Hello wo")
        assertEquals(RichText("Hello wo", listOf(StyleSpan(0, 5, BOLD))), text)
        text = change(text, "Hello w")
        text = change(text, "Hello ")
        text = change(text, "Hello")
        text = change(text, "Hell")
        assertEquals(RichText("Hell", listOf(StyleSpan(0, 4, BOLD))), text)
    }

    @Test
    fun `result is always canonical`() {
        val inputs = listOf("", "p", "plain", "plain bold tail extra", "xx bold yy", "\n\n", "${GRINNING}plain")
        for (input in inputs) {
            val result = change(sample, input)
            assertEquals(input, result.text)
            assertEquals(result, result.normalized())
        }
    }
}
