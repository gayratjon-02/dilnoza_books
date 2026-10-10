package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import dilnoza.books.domain.model.RichText
import dilnoza.books.editor.richtext.RichTextEditor
import dilnoza.books.editor.search.SearchMatch
import dilnoza.books.editor.search.TextSearch

/** The result of replacing matches in one block: its new text and where the old selection moved. */
data class Replaced(val rich: RichText, val selection: TextRange)

/**
 * Replaces [matches] (all from this text) with [replacement]. Each replacement takes the style of the
 * text it replaces, so spans around and across it survive; [selection] follows the edits.
 */
fun RichText.replaceMatches(matches: List<SearchMatch>, replacement: String, selection: TextRange): Replaced {
    // Right to left, so the matches still to do keep their offsets.
    val edits = TextSearch.replacementsFor(text, matches, replacement)
    var result = this
    for (edit in edits) {
        val newText = result.text.replaceRange(edit.start, edit.end, edit.replacement)
        result = RichTextEditor.applyTextChange(result, newText, cursor = edit.start + edit.replacement.length)
    }
    val moved = TextRange(TextSearch.mapOffset(selection.start, edits), TextSearch.mapOffset(selection.end, edits))
    return Replaced(result, moved)
}
