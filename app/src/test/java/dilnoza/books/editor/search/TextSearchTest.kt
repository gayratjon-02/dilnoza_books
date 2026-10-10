package dilnoza.books.editor.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    private fun ranges(text: String, query: String, options: SearchOptions = SearchOptions()) =
        TextSearch.findAll(text, query, options).map { it.first to it.last + 1 }

    private val caseSensitive = SearchOptions(matchCase = true)
    private val wholeWord = SearchOptions(wholeWord = true)

    // region findAll

    @Test
    fun `empty query or empty input finds nothing`() {
        assertTrue(TextSearch.findAll(listOf("a" to "text"), "", SearchOptions()).isEmpty())
        assertTrue(TextSearch.findAll(emptyList(), "x", SearchOptions()).isEmpty())
        assertTrue(ranges("", "x").isEmpty())
        assertTrue(ranges("ab", "abc").isEmpty())
    }

    @Test
    fun `matches are in unit order then left to right`() {
        val units = listOf("b1" to "cat and cat", "b2" to "", "b3" to "Cat")
        val matches = TextSearch.findAll(units, "cat", SearchOptions())
        assertEquals(
            listOf(SearchMatch("b1", 0, 3), SearchMatch("b1", 8, 11), SearchMatch("b3", 0, 3)),
            matches,
        )
    }

    @Test
    fun `matches do not overlap`() {
        assertEquals(listOf(0 to 2), ranges("aaa", "aa"))
        assertEquals(listOf(0 to 2, 2 to 4), ranges("aaaa", "aa"))
        assertEquals(listOf(0 to 3, 3 to 6), ranges("abaaba", "aba"))
    }

    @Test
    fun `whole text match`() {
        assertEquals(listOf(0 to 5), ranges("hello", "hello"))
    }

    @Test
    fun `case insensitive by default for latin and cyrillic`() {
        assertEquals(listOf(0 to 5, 6 to 11), ranges("Hello HELLO", "hello"))
        assertEquals(listOf(0 to 5, 6 to 11), ranges("Китоб КИТОБ", "китоб"))
        assertEquals(listOf(0 to 4), ranges("Ёлка", "ёЛКА"))
        assertEquals(listOf(0 to 5), ranges("Ўзбек", "ўЗБЕК"))
    }

    @Test
    fun `match case respects case`() {
        assertEquals(listOf(6 to 11), ranges("hello Hello", "Hello", caseSensitive))
        assertEquals(listOf(0 to 5), ranges("Китоб китоб", "Китоб", caseSensitive))
    }

    @Test
    fun `turkish dotted and dotless i fold without shifting indices`() {
        // "İ".lowercase() is two chars; folding per code point keeps match ranges aligned.
        assertEquals(listOf(0 to 8), ranges("İstanbul", "istanbul"))
        assertEquals(listOf(4 to 7), ranges("İİİ kit", "KIT"))
        assertEquals(listOf(0 to 4), ranges("ılık!", "ILIK"))
    }

    @Test
    fun `greek final sigma and german sharp s fold`() {
        assertEquals(listOf(0 to 5), ranges("ΛΟΓΟΣ", "λογος"))
        assertEquals(listOf(0 to 5), ranges("λογος", "ΛΟΓΟΣ"))
        assertEquals(listOf(0 to 1), ranges("ẞ", "ß"))
    }

    @Test
    fun `uzbek apostrophes are interchangeable`() {
        val text = "Oʻzbekiston va gʻalaba"
        assertEquals(listOf(0 to 11), ranges(text, "o'zbekiston"))
        assertEquals(listOf(0 to 11), ranges(text, "O‘zbekiston"))
        assertEquals(listOf(0 to 11), ranges(text, "o`zbekiston"))
        assertEquals(listOf(15 to 22), ranges(text, "gʻalaba"))
        assertEquals(listOf(0 to 6), ranges("san'at", "sanʼat"))
    }

    @Test
    fun `apostrophes can be compared strictly`() {
        val strict = SearchOptions(unifyApostrophes = false)
        assertTrue(ranges("Oʻzbekiston", "o'zbekiston", strict).isEmpty())
        assertEquals(listOf(0 to 11), ranges("Oʻzbekiston", "oʻzbekiston", strict))
    }

    @Test
    fun `apostrophe unification also applies with match case`() {
        assertEquals(listOf(0 to 2), ranges("oʻ", "o'", caseSensitive))
        assertTrue(ranges("Oʻ", "o'", caseSensitive).isEmpty())
    }

    @Test
    fun `surrogate pairs are matched by code point and never split`() {
        val text = "a😀b😀c"
        assertEquals(listOf(1 to 3, 4 to 6), ranges(text, "😀"))
        assertEquals(listOf(1 to 4), ranges(text, "😀b"))
        // A lone high surrogate must not match half of an emoji.
        assertTrue(ranges(text, "\uD83D").isEmpty())
    }

    @Test
    fun `case folding works for supplementary letters`() {
        // Deseret capital and small long I.
        assertEquals(listOf(0 to 2), ranges("\uD801\uDC00", "\uD801\uDC28"))
        assertEquals(listOf(2 to 4), ranges("x \uD801\uDC28", "\uD801\uDC00"))
    }

    @Test
    fun `indices after emoji are utf16 offsets`() {
        val text = "👍🏽 Salom"
        val (start, end) = ranges(text, "salom").single()
        assertEquals("Salom", text.substring(start, end))
    }

    // endregion

    // region whole word

    @Test
    fun `whole word rejects matches inside words`() {
        assertEquals(listOf(5 to 8), ranges("cats cat scat", "cat", wholeWord))
        assertEquals(listOf(0 to 3, 4 to 7), ranges("cat,cat", "cat", wholeWord))
    }

    @Test
    fun `whole word uses unicode letters and digits`() {
        assertTrue(ranges("китобим", "китоб", wholeWord).isEmpty())
        assertEquals(listOf(0 to 5), ranges("китоб.", "китоб", wholeWord))
        assertTrue(ranges("cat2", "cat", wholeWord).isEmpty())
        assertTrue(ranges("2cat", "cat", wholeWord).isEmpty())
        assertEquals(listOf(1 to 4), ranges("(cat)", "cat", wholeWord))
    }

    @Test
    fun `whole word next to emoji or punctuation is a boundary`() {
        assertEquals(listOf(2 to 5), ranges("😀cat😀", "cat", wholeWord))
    }

    @Test
    fun `combining marks belong to the word`() {
        // "café" with a decomposed e + U+0301.
        assertTrue(ranges("cafe\u0301", "cafe", wholeWord).isEmpty())
        assertEquals(listOf(0 to 4), ranges("cafe\u0301", "cafe"))
    }

    @Test
    fun `apostrophe between letters is part of the word`() {
        assertTrue(ranges("o'zbek", "zbek", wholeWord).isEmpty())
        assertTrue(ranges("oʻzbek", "zbek", wholeWord).isEmpty())
        assertTrue(ranges("oʻzbek", "o", wholeWord).isEmpty())
        assertEquals(listOf(0 to 6), ranges("oʻzbek tili", "o'zbek", wholeWord))
    }

    @Test
    fun `quoting apostrophes are word boundaries`() {
        assertEquals(listOf(1 to 6), ranges("'kitob'", "kitob", wholeWord))
        assertEquals(listOf(1 to 6), ranges("‘kitob’", "kitob", wholeWord))
        assertEquals(listOf(1 to 6), ranges("ʻkitobʻ", "kitob", wholeWord))
    }

    @Test
    fun `whole word skips a rejected candidate and finds an overlapping one`() {
        // Candidates at 0 and 1 sit inside "aaa"; the search must keep going to the word at 4.
        assertEquals(listOf(4 to 6), ranges("aaa aa", "aa", wholeWord))
    }

    // endregion

    // region replace

    @Test
    fun `replacements are ordered right to left`() {
        val text = "cat cat cat"
        val matches = TextSearch.findAll(listOf("b" to text), "cat", SearchOptions())
        val edits = TextSearch.replacementsFor(text, matches, "dog")
        assertEquals(listOf(8, 4, 0), edits.map { it.start })
        assertEquals("dog dog dog", TextSearch.applyReplacements(text, edits))
    }

    @Test
    fun `sequential application in returned order keeps indices valid`() {
        val text = "ab ab ab"
        val edits = TextSearch.replacementsForRanges(text, TextSearch.findAll(text, "ab"), "xyz")
        var result = text
        for (e in edits) result = result.replaceRange(e.start, e.end, e.replacement)
        assertEquals("xyz xyz xyz", result)
    }

    @Test
    fun `replacement can be longer, shorter or empty`() {
        val text = "a-b-c"
        val ranges = TextSearch.findAll(text, "-")
        assertEquals("a---b---c", TextSearch.applyReplacements(text, TextSearch.replacementsForRanges(text, ranges, "---")))
        assertEquals("abc", TextSearch.applyReplacements(text, TextSearch.replacementsForRanges(text, ranges, "")))
    }

    @Test
    fun `stale or overlapping matches are skipped`() {
        val text = "hello"
        val edits = TextSearch.replacementsForRanges(text, listOf(0 until 2, 1 until 3, 3 until 9, 3 until 5), "X")
        assertEquals(listOf(TextReplacement(3, 5, "X"), TextReplacement(0, 2, "X")), edits)
        assertEquals("XlX", TextSearch.applyReplacements(text, edits))
    }

    @Test
    fun `no matches leaves text unchanged`() {
        assertTrue(TextSearch.replacementsFor("abc", emptyList(), "x").isEmpty())
        assertEquals("abc", TextSearch.applyReplacements("abc", emptyList()))
    }

    @Test
    fun `apply accepts any order`() {
        val edits = listOf(TextReplacement(0, 1, "A"), TextReplacement(2, 3, "C"))
        assertEquals("AbC", TextSearch.applyReplacements("abc", edits))
        assertEquals("AbC", TextSearch.applyReplacements("abc", edits.reversed()))
    }

    @Test
    fun `apply supports pure insertions`() {
        assertEquals(">abc<", TextSearch.applyReplacements("abc", listOf(TextReplacement(3, 3, "<"), TextReplacement(0, 0, ">"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply rejects overlapping edits`() {
        TextSearch.applyReplacements("abcdef", listOf(TextReplacement(0, 3, "x"), TextReplacement(2, 4, "y")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply rejects out of bounds edits`() {
        TextSearch.applyReplacements("abc", listOf(TextReplacement(2, 4, "x")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply rejects inverted ranges`() {
        TextSearch.applyReplacements("abc", listOf(TextReplacement(2, 1, "x")))
    }

    @Test
    fun `replace emoji and case insensitive uzbek`() {
        val text = "Oʻzbek 😀 oʻzbek"
        val ranges = TextSearch.findAll(text, "o'zbek")
        assertEquals("tili 😀 tili", TextSearch.applyReplacements(text, TextSearch.replacementsForRanges(text, ranges, "tili")))
        val emoji = TextSearch.findAll(text, "😀")
        assertEquals("Oʻzbek :) oʻzbek", TextSearch.applyReplacements(text, TextSearch.replacementsForRanges(text, emoji, ":)")))
    }

    @Test
    fun `mapOffset shifts positions after edits and snaps positions inside an edit`() {
        // "a cat b cat" -> "a lion b lion"
        val edits = listOf(TextReplacement(8, 11, "lion"), TextReplacement(2, 5, "lion"))
        assertEquals(0, TextSearch.mapOffset(0, edits))
        assertEquals(2, TextSearch.mapOffset(2, edits))
        assertEquals(6, TextSearch.mapOffset(3, edits))
        assertEquals(6, TextSearch.mapOffset(5, edits))
        assertEquals(8, TextSearch.mapOffset(7, edits))
        assertEquals(13, TextSearch.mapOffset(11, edits))
        assertEquals(0, TextSearch.mapOffset(0, emptyList()))
    }

    @Test
    fun `mapOffset moves past an insertion at the offset`() {
        assertEquals(4, TextSearch.mapOffset(1, listOf(TextReplacement(1, 1, "xyz"))))
    }

    // endregion
}
