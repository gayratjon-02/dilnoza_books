package dilnoza.books.editor.commands

import dilnoza.books.editor.commands.MarkdownShortcuts.detect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownShortcutsTest {

    /** Simulates typing [char] at [at] in [old]; returns the detection result. */
    private fun type(old: String, char: Char, at: Int = old.length): ShortcutMatch? {
        val new = old.substring(0, at) + char + old.substring(at)
        return detect(old, new, at + 1)
    }

    @Test
    fun `heading markers`() {
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_1, 2), type("#", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_2, 3), type("##", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_3, 4), type("###", ' '))
    }

    @Test
    fun `four hashes is not a heading`() {
        assertNull(type("####", ' '))
    }

    @Test
    fun `bullet markers`() {
        assertEquals(ShortcutMatch(ShortcutTarget.BULLET_LIST, 2), type("-", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.BULLET_LIST, 2), type("*", ' '))
    }

    @Test
    fun `quote marker`() {
        assertEquals(ShortcutMatch(ShortcutTarget.QUOTE, 2), type(">", ' '))
    }

    @Test
    fun `numbered markers with any number`() {
        assertEquals(ShortcutMatch(ShortcutTarget.NUMBERED_LIST, 3), type("1.", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.NUMBERED_LIST, 4), type("12.", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.NUMBERED_LIST, 3), type("0.", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.NUMBERED_LIST, 11), type("123456789.", ' '))
    }

    @Test
    fun `invalid numbered markers`() {
        assertNull(type(".", ' '))
        assertNull(type("1", ' '))
        assertNull(type("1)", ' '))
        assertNull(type("a.", ' '))
        assertNull(type("1.2.", ' '))
        assertNull(type("-1.", ' '))
        assertNull(type("1234567890.", ' '))
        assertNull(type("١.", ' '))
    }

    @Test
    fun `divider on third dash when it is the whole text`() {
        assertEquals(ShortcutMatch(ShortcutTarget.DIVIDER, 3), type("--", '-'))
        assertEquals(ShortcutMatch(ShortcutTarget.DIVIDER, 3), type("--", '-', at = 0))
        assertEquals(ShortcutMatch(ShortcutTarget.DIVIDER, 3), type("--", '-', at = 1))
    }

    @Test
    fun `dashes that are not exactly three do not make a divider`() {
        assertNull(type("-", '-'))
        assertNull(type("---", '-'))
        assertNull(type("--x", '-', at = 2))
        assertNull(type("--", ' '))
    }

    @Test
    fun `marker followed by existing text still converts`() {
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_1, 2), type("#Title", ' ', at = 1))
        assertEquals(ShortcutMatch(ShortcutTarget.BULLET_LIST, 2), type("-item", ' ', at = 1))
    }

    @Test
    fun `marker not at index zero does not trigger`() {
        assertNull(type("a #", ' '))
        assertNull(type(" #", ' '))
        assertNull(type("text -", ' '))
        assertNull(type("x1.", ' '))
    }

    @Test
    fun `typing other characters after a marker does not trigger`() {
        assertNull(type("#", 'a'))
        assertNull(type("#", '\t'))
        assertNull(type("-", ' '))
    }

    @Test
    fun `space typed inside the text only matches the prefix before it`() {
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_1, 2), type("##", ' ', at = 1))
        assertNull(type("#", ' ', at = 0))
        assertNull(type("1.", ' ', at = 1))
    }

    @Test
    fun `paste of longer text does not trigger`() {
        assertNull(detect("", "# ", 2))
        assertNull(detect("", "# Heading", 2))
        assertNull(detect("#", "# pasted", 2))
        assertNull(detect("", "---", 3))
        assertNull(detect("-", "---", 3))
    }

    @Test
    fun `deletion does not trigger`() {
        assertNull(detect("# x", "# ", 2))
        assertNull(detect("----", "---", 3))
        assertNull(detect("1. a", "1. ", 3))
    }

    @Test
    fun `replacement of the same length does not trigger`() {
        assertNull(detect("#a", "# ", 2))
        assertNull(detect("--x", "---", 3))
    }

    @Test
    fun `cursor not at the inserted character does not trigger`() {
        assertNull(detect("#", "# ", 1))
        assertNull(detect("#", "# ", 0))
        assertNull(detect("#x", "# x", 3))
    }

    @Test
    fun `out of range cursor is rejected`() {
        assertNull(detect("#", "# ", 3))
        assertNull(detect("#", "# ", -1))
    }

    @Test
    fun `empty texts`() {
        assertNull(detect("", "", 0))
        assertNull(type("", ' '))
        assertNull(type("", '-'))
    }

    @Test
    fun `emoji before or after the marker`() {
        assertNull(type("😀#", ' '))
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_1, 2), type("#😀", ' ', at = 1))
    }

    @Test
    fun `inserted text identical to neighbours is not mistaken for a single char`() {
        // Inserting "#" before "# " looks like a 1-char insertion, but cursor sits after the typed '#', not a space.
        assertNull(detect("# ", "## ", 1))
        assertEquals(ShortcutMatch(ShortcutTarget.HEADING_2, 3), detect("##", "## ", 3))
    }
}
