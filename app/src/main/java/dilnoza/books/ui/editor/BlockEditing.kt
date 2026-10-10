package dilnoza.books.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.editor.commands.MarkdownShortcuts
import dilnoza.books.editor.commands.ShortcutMatch
import dilnoza.books.editor.commands.ShortcutTarget
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
        val shortcut = if (breaks.isEmpty() && block.kind == BlockKind.PARAGRAPH) {
            MarkdownShortcuts.detect(block.value.text, value.text, cursor)
        } else {
            null
        }
        if (breaks.isEmpty() && shortcut == null) {
            block.setValue(value, typingStyle)
            return BlockEdit.Content(block.id)
        }
        val edited = RichTextEditor.applyTextChange(block.rich, value.text, typingStyle, cursor)
        return if (shortcut != null) {
            applyShortcut(block, edited, shortcut, cursor)
        } else {
            splitLines(block, edited, breaks, cursor)
        }
    }

    /**
     * Replaces [start]..[end] of [block] (the "/" command typed into it) with a block of [kind]. An
     * emptied block becomes the new kind itself; otherwise the new block goes right after it. A text
     * block always follows a non-text one; the caret moves there, or into the first cell of a [table].
     */
    fun insert(
        block: EditorBlock,
        start: Int,
        end: Int,
        kind: BlockKind,
        headingLevel: Int = block.headingLevel,
        table: TableState = TableState.create(TableState.DEFAULT_SIZE, TableState.DEFAULT_SIZE, headerRow = true),
    ): BlockEdit {
        val length = block.rich.text.length
        val rest = block.rich.subText(0, start) + block.rich.subText(end, length)
        block.setRich(rest, TextRange(start.coerceIn(0, rest.text.length)))
        val index = blocks.indexOf(block)
        if (kind.isText) {
            if (rest.text.isEmpty()) {
                convert(block, kind, headingLevel)
                return BlockEdit.Structure(focusId = block.id)
            }
            val created = newBlock(kind, RichText.Empty).also { it.headingLevel = headingLevel }
            blocks.add(index + 1, created)
            return BlockEdit.Structure(focusId = created.id)
        }
        val target = if (rest.text.isEmpty()) {
            block.also { it.kind = kind }
        } else {
            newBlock(kind, RichText.Empty).also { blocks.add(index + 1, it) }
        }
        val after = textBlockAfter(target)
        if (kind == BlockKind.TABLE) {
            target.table = table
            return BlockEdit.Structure(focusId = target.id)
        }
        return BlockEdit.Structure(focusId = after.id)
    }

    /** Turns a text block into another text kind; headings cannot hold line breaks. */
    fun convert(block: EditorBlock, kind: BlockKind, headingLevel: Int = block.headingLevel) {
        block.kind = kind
        if (kind == BlockKind.HEADING) {
            block.headingLevel = headingLevel
            // Same length, so spans and selection still line up.
            if ('\n' in block.rich.text) block.setRich(block.rich.withoutLineBreaksIf(true), block.value.selection)
        }
    }

    private fun applyShortcut(block: EditorBlock, edited: RichText, match: ShortcutMatch, cursor: Int): BlockEdit {
        val rest = edited.subText(match.markerLength, edited.text.length)
        block.setRich(rest, TextRange((cursor - match.markerLength).coerceIn(0, rest.text.length)))
        when (match.target) {
            ShortcutTarget.HEADING_1 -> convert(block, BlockKind.HEADING, 1)
            ShortcutTarget.HEADING_2 -> convert(block, BlockKind.HEADING, 2)
            ShortcutTarget.HEADING_3 -> convert(block, BlockKind.HEADING, 3)
            ShortcutTarget.BULLET_LIST -> convert(block, BlockKind.BULLET)
            ShortcutTarget.NUMBERED_LIST -> convert(block, BlockKind.NUMBERED)
            ShortcutTarget.QUOTE -> convert(block, BlockKind.QUOTE)
            ShortcutTarget.DIVIDER -> {
                block.kind = BlockKind.DIVIDER
                return BlockEdit.Structure(focusId = textBlockAfter(block).id)
            }
        }
        return BlockEdit.Content(block.id)
    }

    /** The empty text block right after [block], adding a paragraph there if there is none. */
    fun textBlockAfter(block: EditorBlock): EditorBlock {
        val index = blocks.indexOf(block)
        val next = blocks.getOrNull(index + 1)
        if (next != null && next.kind.isText && next.value.text.isEmpty()) return next
        return newBlock(BlockKind.PARAGRAPH, RichText.Empty).also { blocks.add(index + 1, it) }
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
