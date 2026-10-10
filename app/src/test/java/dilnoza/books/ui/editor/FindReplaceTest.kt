package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.StyleSpan
import dilnoza.books.editor.search.SearchMatch
import dilnoza.books.editor.search.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class FindReplaceTest {

    private val bold = SpanStyleData(bold = true)
    private val italic = SpanStyleData(italic = true)

    private fun matches(text: String, query: String) = TextSearch.findAll(listOf("b" to text), query)

    @Test
    fun `every match is replaced and surrounding spans stay`() {
        val rich = RichText("olma va olma", listOf(StyleSpan(5, 7, italic)))

        val result = rich.replaceMatches(matches(rich.text, "olma"), "nok", TextRange(0))

        assertEquals(RichText("nok va nok", listOf(StyleSpan(4, 6, italic))), result.rich)
    }

    @Test
    fun `the replacement takes the style of the replaced word`() {
        val rich = RichText("Salom dunyo", listOf(StyleSpan(0, 5, bold)))

        val result = rich.replaceMatches(matches(rich.text, "salom"), "Assalomu", TextRange(0))

        assertEquals(RichText("Assalomu dunyo", listOf(StyleSpan(0, 8, bold))), result.rich)
    }

    @Test
    fun `a replacement containing the query is not replaced again`() {
        val rich = RichText("a b a")

        val result = rich.replaceMatches(matches(rich.text, "a"), "aa", TextRange(0))

        assertEquals("aa b aa", result.rich.text)
    }

    @Test
    fun `the selection follows the edits`() {
        val rich = RichText("kitob va kitob")

        val result = rich.replaceMatches(matches(rich.text, "kitob"), "daftar", TextRange(14))

        assertEquals(TextRange(16), result.selection)
    }

    @Test
    fun `replacing with nothing deletes the matches`() {
        val rich = RichText("bir, ikki, uch")

        val result = rich.replaceMatches(matches(rich.text, ","), "", TextRange(0))

        assertEquals("bir ikki uch", result.rich.text)
    }

    @Test
    fun `only the given matches are replaced`() {
        val rich = RichText("x x x")

        val result = rich.replaceMatches(listOf(SearchMatch("b", 2, 3)), "y", TextRange(0))

        assertEquals("x y x", result.rich.text)
    }
}
