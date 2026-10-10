package dilnoza.books.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.TextAlignment

enum class BlockKind { PARAGRAPH, HEADING }

/** Mutable, in-memory block while a chapter is open; the editor's source of truth until it is saved. */
@Stable
class EditorBlock(
    val id: String,
    kind: BlockKind,
    value: TextFieldValue,
    val align: TextAlignment = TextAlignment.START,
) {
    var kind by mutableStateOf(kind)
    var value by mutableStateOf(value)
    val focusRequester = FocusRequester()

    fun toBlock(chapterId: String, position: Int) = Block(
        id = id,
        chapterId = chapterId,
        position = position,
        content = when (kind) {
            BlockKind.HEADING -> BlockContent.Heading(level = 1, text = RichText(value.text))
            BlockKind.PARAGRAPH -> BlockContent.Paragraph(text = RichText(value.text), align = align)
        },
    )

    companion object {
        fun from(block: Block): EditorBlock = when (val content = block.content) {
            is BlockContent.Heading -> EditorBlock(block.id, BlockKind.HEADING, TextFieldValue(content.text.text))
            is BlockContent.Paragraph ->
                EditorBlock(block.id, BlockKind.PARAGRAPH, TextFieldValue(content.text.text), content.align)
        }
    }
}
