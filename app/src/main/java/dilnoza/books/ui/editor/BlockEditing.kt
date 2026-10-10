package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.editor.richtext.RichTextEditor
import dilnoza.books.editor.richtext.plus
import dilnoza.books.editor.richtext.subText

/** What an edit did to the chapter, so the caller knows what to save and where the caret goes. */
sealed interface BlockEdit {
    /** Only this block changed; the order of blocks did not. */
    data class Content(val blockId: String) : BlockEdit

    /**
     * Blocks were added, removed or reordered. [focusId] gets the caret (its selection is already set);
     * [selectId] is a non-text block to select instead.
     */
    data class Structure(
        val focusId: String?,
        val removedIds: List<String> = emptyList(),
        val selectId: String? = null,
    ) : BlockEdit
}

/** Word-processor editing that crosses block boundaries: splitting on Enter, merging, converting. */
class BlockEditing(
    private val blocks: MutableList<EditorBlock>,
    private val newBlock: (BlockKind, RichText) -> EditorBlock,
) {

    /** Applies a text change to a text block; line breaks typed or pasted into it split the block. */
    fun type(block: EditorBlock, value: TextFieldValue, typingStyle: SpanStyleData?): BlockEdit {
        val cursor = value.selection.end
        val breaks = insertedLineBreaks(block.value.text, value.text, cursor)
        if (breaks.isEmpty()) {
            block.setValue(value, typingStyle)
            return BlockEdit.Content(block.id)
        }
        val edited = RichTextEditor.applyTextChange(block.rich, value.text, typingStyle, cursor)
        return splitLines(block, edited, breaks, cursor)
    }

    private fun splitLines(block: EditorBlock, edited: RichText, breaks: List<Int>, cursor: Int): BlockEdit {
        // Enter on an empty item ends the list (or quote) instead of adding another empty one.
        if (edited.text == "\n" && block.kind in EXITABLE) {
            block.setRich(RichText.Empty, TextRange(0))
            return exit(block)
        }
        val segments = buildList {
            var start = 0
            for (at in breaks) {
                add(edited.subText(start, at))
                start = at + 1
            }
            add(edited.subText(start, edited.text.length))
        }
        val index = blocks.indexOf(block)
        val followerKind = if (block.kind == BlockKind.HEADING) BlockKind.PARAGRAPH else block.kind
        if (segments.size == 2 && segments[0].text.isEmpty() && segments[1].text.isNotEmpty()) {
            // Enter at the very start pushes the block down, keeping its kind, instead of moving its text.
            blocks.add(index, follower(block, followerKind, RichText.Empty))
            block.setRich(segments[1], TextRange(0))
            return BlockEdit.Structure(focusId = block.id)
        }
        block.setRich(segments[0].withoutLineBreaksIf(block.kind == BlockKind.HEADING), TextRange(segments[0].text.length))
        val created = segments.drop(1).map { follower(block, followerKind, it) }
        blocks.addAll(index + 1, created)
        // The caret ends up in the segment it was typed into, at the same place.
        val segment = breaks.count { it < cursor }
        val target = if (segment == 0) block else created[segment - 1]
        val offset = if (segment == 0) cursor else cursor - breaks[segment - 1] - 1
        target.setSelection(TextRange(offset.coerceIn(0, target.value.text.length)))
        return BlockEdit.Structure(focusId = target.id)
    }

    /**
     * Backspace with the caret at the start of [block]. A list item, quote or heading first becomes a
     * paragraph; a paragraph joins the text block before it. A divider or page break before it is
     * deleted, and a picture or table is selected, so it is never removed by a stray keypress.
     */
    fun backspaceAtStart(block: EditorBlock): BlockEdit? {
        if (block.kind != BlockKind.PARAGRAPH) return exit(block)
        val index = blocks.indexOf(block)
        val previous = blocks.getOrNull(index - 1) ?: return null
        return when {
            previous.kind.isText -> join(previous, block)
            previous.kind == BlockKind.DIVIDER || previous.kind == BlockKind.PAGE_BREAK -> {
                blocks.removeAt(index - 1)
                block.setSelection(TextRange(0))
                BlockEdit.Structure(focusId = block.id, removedIds = listOf(previous.id))
            }
            block.value.text.isEmpty() -> {
                blocks.removeAt(index)
                BlockEdit.Structure(focusId = null, removedIds = listOf(block.id), selectId = previous.id)
            }
            else -> BlockEdit.Structure(focusId = null, selectId = previous.id)
        }
    }

    /** Forward delete with the caret at the end of [block]: pulls the next block's text up into it. */
    fun deleteAtEnd(block: EditorBlock): BlockEdit? {
        val index = blocks.indexOf(block)
        val next = blocks.getOrNull(index + 1) ?: return null
        return when {
            next.kind.isText -> join(block, next)
            next.kind == BlockKind.DIVIDER || next.kind == BlockKind.PAGE_BREAK -> {
                blocks.removeAt(index + 1)
                BlockEdit.Structure(focusId = block.id, removedIds = listOf(next.id))
            }
            else -> BlockEdit.Structure(focusId = null, selectId = next.id)
        }
    }

    /** Appends [second]'s text to [first] and removes [second]; the caret goes to the seam. */
    private fun join(first: EditorBlock, second: EditorBlock): BlockEdit {
        val seam = first.rich.text.length
        first.setRich((first.rich + second.rich).withoutLineBreaksIf(first.kind == BlockKind.HEADING), TextRange(seam))
        blocks.remove(second)
        return BlockEdit.Structure(focusId = first.id, removedIds = listOf(second.id))
    }

    /** Leaves a list level, or turns a list item, quote or heading back into a paragraph. */
    private fun exit(block: EditorBlock): BlockEdit {
        if (block.kind in LISTS && block.indent > 0) {
            block.indent--
        } else {
            block.kind = BlockKind.PARAGRAPH
        }
        return BlockEdit.Content(block.id)
    }

    private fun follower(block: EditorBlock, kind: BlockKind, rich: RichText): EditorBlock =
        newBlock(kind, rich).also {
            it.indent = block.indent
            if (kind == block.kind) it.align = block.align
        }

    companion object {
        private val LISTS = setOf(BlockKind.BULLET, BlockKind.NUMBERED)
        private val EXITABLE = LISTS + BlockKind.QUOTE

        /**
         * Positions in [new] of line breaks that the edit ending at [cursor] inserted. Line breaks
         * already in the text (from older versions of a paragraph) are not counted.
         */
        fun insertedLineBreaks(old: String, new: String, cursor: Int): List<Int> {
            if (cursor !in 0..new.length) return emptyList()
            val prefix = old.commonPrefixWith(new).length
            val start = minOf(prefix, cursor - maxOf(0, new.length - old.length)).coerceAtLeast(0)
            return (start until cursor).filter { new[it] == '\n' }
        }

        private fun RichText.withoutLineBreaksIf(condition: Boolean): RichText =
            if (condition && '\n' in text) copy(text = text.replace('\n', ' ')) else this
    }
}
