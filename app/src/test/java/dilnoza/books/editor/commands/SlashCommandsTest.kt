package dilnoza.books.editor.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlashCommandsTest {

    private fun query(text: String, cursor: Int = text.length) = SlashCommands.query(text, cursor)

    @Test
    fun `slash at start of text opens an empty query`() {
        assertEquals(SlashQuery(0, ""), query("/"))
    }

    @Test
    fun `filter is the text between slash and cursor`() {
        assertEquals(SlashQuery(0, "head"), query("/head"))
        assertEquals(SlashQuery(0, "he"), query("/heading", cursor = 3))
    }

    @Test
    fun `slash after whitespace`() {
        assertEquals(SlashQuery(6, "img"), query("Hello /img"))
        assertEquals(SlashQuery(1, "q"), query("\n/q"))
        assertEquals(SlashQuery(1, ""), query("\t/"))
    }

    @Test
    fun `slash inside a word is ignored`() {
        assertNull(query("and/or"))
        assertNull(query("a/"))
        assertNull(query("http://x"))
    }

    @Test
    fun `whitespace between slash and cursor closes the query`() {
        assertNull(query("/head ing"))
        assertNull(query("/ "))
        assertNull(query("/head\n"))
    }

    @Test
    fun `no slash`() {
        assertNull(query("hello"))
        assertNull(query("hello world"))
    }

    @Test
    fun `cursor before the slash`() {
        assertNull(query("ab /x", cursor = 2))
        assertNull(query("/x", cursor = 0))
    }

    @Test
    fun `cursor right after the slash with text following`() {
        assertEquals(SlashQuery(0, ""), query("/heading", cursor = 1))
    }

    @Test
    fun `nearest slash wins and must be at a word start`() {
        assertNull(query("/a/b"))
        assertEquals(SlashQuery(3, "b"), query("/a /b"))
        assertNull(query("//"))
    }

    @Test
    fun `empty text and invalid cursors`() {
        assertNull(query(""))
        assertNull(query("", cursor = 0))
        assertNull(query("/x", cursor = 3))
        assertNull(query("/x", cursor = -1))
    }

    @Test
    fun `emoji in filter keeps surrogate pairs intact`() {
        val text = "hi /😀x"
        assertEquals(SlashQuery(3, "😀x"), query(text))
    }

    @Test
    fun `emoji before the slash without space is a word char`() {
        assertNull(query("😀/x"))
    }

    private data class Cmd(val id: String, val words: List<String>)

    private val heading = Cmd("h", listOf("Heading", "Title"))
    private val image = Cmd("i", listOf("Image", "Picture", "Photo"))
    private val quote = Cmd("q", listOf("Quote", "Citation"))
    private val subheading = Cmd("s", listOf("Subheading"))
    private val all = listOf(subheading, heading, image, quote)

    private fun filter(f: String) = SlashCommands.filter(all, f) { it.words }.map { it.id }

    @Test
    fun `blank filter keeps every command in order`() {
        assertEquals(listOf("s", "h", "i", "q"), filter(""))
        assertEquals(listOf("s", "h", "i", "q"), filter("  "))
    }

    @Test
    fun `prefix matches come before substring matches`() {
        assertEquals(listOf("h", "s"), filter("head"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals(listOf("i"), filter("PIC"))
        assertEquals(listOf("i"), filter("pHoTo"))
    }

    @Test
    fun `any keyword can match`() {
        assertEquals(listOf("q"), filter("cit"))
        assertEquals(listOf("h"), filter("tit"))
    }

    @Test
    fun `substring only match`() {
        assertEquals(listOf("i"), filter("tur"))
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<String>(), filter("xyz"))
    }

    @Test
    fun `empty command list`() {
        assertEquals(emptyList<Cmd>(), SlashCommands.filter(emptyList<Cmd>(), "a") { it.words })
    }

    @Test
    fun `non latin keywords`() {
        val cmds = listOf(Cmd("ru", listOf("Заголовок")), Cmd("uz", listOf("Sarlavha", "Oʻchirish")))
        assertEquals(listOf("ru"), SlashCommands.filter(cmds, "заг") { it.words }.map { it.id })
        assertEquals(listOf("uz"), SlashCommands.filter(cmds, "oʻch") { it.words }.map { it.id })
    }
}
