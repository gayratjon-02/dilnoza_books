package dilnoza.books.editor.commands

import dilnoza.books.editor.commands.ListNumbering.labels
import org.junit.Assert.assertEquals
import org.junit.Test

class ListNumberingTest {

    private fun o(indent: Int = 0) = ListItem(ListKind.ORDERED, indent)
    private fun b(indent: Int = 0) = ListItem(ListKind.BULLET, indent)
    private val n = ListItem.NONE

    @Test
    fun `empty input`() {
        assertEquals(emptyList<String?>(), labels(emptyList()))
    }

    @Test
    fun `non list blocks get no label`() {
        assertEquals(listOf(null, null), labels(listOf(n, n)))
    }

    @Test
    fun `consecutive ordered items count up`() {
        assertEquals(listOf("1.", "2.", "3."), labels(listOf(o(), o(), o())))
    }

    @Test
    fun `numbering restarts after a non list block`() {
        assertEquals(listOf("1.", "2.", null, "1."), labels(listOf(o(), o(), n, o())))
    }

    @Test
    fun `numbering restarts after a bullet at the same level`() {
        assertEquals(listOf("1.", "•", "1."), labels(listOf(o(), b(), o())))
    }

    @Test
    fun `bullet glyphs by indent`() {
        assertEquals(listOf("•", "◦", "▪"), labels(listOf(b(0), b(1), b(2))))
    }

    @Test
    fun `nested levels use letters and roman numerals`() {
        assertEquals(
            listOf("1.", "a.", "b.", "i.", "ii.", "c.", "2."),
            labels(listOf(o(0), o(1), o(1), o(2), o(2), o(1), o(0))),
        )
    }

    @Test
    fun `nested level restarts when parent advances`() {
        assertEquals(
            listOf("1.", "a.", "b.", "2.", "a.", "i.", "b.", "i."),
            labels(listOf(o(0), o(1), o(1), o(0), o(1), o(2), o(1), o(2))),
        )
    }

    @Test
    fun `nested ordered list under a bullet parent`() {
        assertEquals(
            listOf("•", "a.", "b.", "•", "a."),
            labels(listOf(b(0), o(1), o(1), b(0), o(1))),
        )
    }

    @Test
    fun `nested bullet does not reset the parent counter`() {
        assertEquals(
            listOf("1.", "◦", "◦", "2."),
            labels(listOf(o(0), b(1), b(1), o(0))),
        )
    }

    @Test
    fun `deeper items do not disturb shallower counters`() {
        assertEquals(
            listOf("1.", "i.", "ii.", "a.", "2."),
            labels(listOf(o(0), o(2), o(2), o(1), o(0))),
        )
    }

    @Test
    fun `non list block resets every level`() {
        assertEquals(
            listOf("1.", "a.", null, "a.", "1."),
            labels(listOf(o(0), o(1), n, o(1), o(0))),
        )
    }

    @Test
    fun `indent is clamped`() {
        assertEquals(listOf("1.", "i.", "ii.", "▪", "•"), labels(listOf(o(-3), o(5), o(99), b(7), b(-1))))
    }

    @Test
    fun `none ignores its indent`() {
        assertEquals(listOf(null), labels(listOf(ListItem(ListKind.NONE, 9))))
    }

    @Test
    fun `letters roll over after z`() {
        val items = listOf(o(0)) + List(28) { o(1) }
        val result = labels(items)
        assertEquals("z.", result[26])
        assertEquals("aa.", result[27])
        assertEquals("ab.", result[28])
    }

    @Test
    fun `roman numerals`() {
        val result = labels(List(50) { o(2) })
        assertEquals("iv.", result[3])
        assertEquals("ix.", result[8])
        assertEquals("xiv.", result[13])
        assertEquals("xl.", result[39])
        assertEquals("xlix.", result[48])
    }

    @Test
    fun `roman numerals fall back to decimal beyond 3999`() {
        val result = labels(List(4000) { o(2) })
        assertEquals("mmmcmxcix.", result[3998])
        assertEquals("4000.", result[3999])
    }

    @Test
    fun `output size matches input`() {
        val items = listOf(n, o(), b(1), n, o(2))
        assertEquals(items.size, labels(items).size)
    }
}
