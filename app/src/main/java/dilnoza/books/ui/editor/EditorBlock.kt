package dilnoza.books.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dilnoza.books.domain.model.Block
import dilnoza.books.domain.model.BlockContent
import dilnoza.books.domain.model.RichText
import dilnoza.books.domain.model.SpanStyleData
import dilnoza.books.domain.model.TextAlignment
import dilnoza.books.editor.richtext.RichTextEditor
import dilnoza.books.editor.richtext.normalized

enum class BlockKind(val isText: Boolean) {
    PARAGRAPH(true),
    HEADING(true),
    BULLET(true),
    NUMBERED(true),
    QUOTE(true),
    DIVIDER(false),
    PAGE_BREAK(false),
    TABLE(false),
    IMAGE(false),
}

/** Image part of an image block; [fileName] is null while the picture is still being imported. */
data class ImageState(
    val fileName: String?,
    val width: Int,
    val height: Int,
    val widthPercent: Int = BlockContent.Image.MAX_WIDTH_PERCENT,
    val align: TextAlignment = TextAlignment.CENTER,
) {
    val aspectRatio: Float get() = width.toFloat() / height

    companion object {
        val Pending = ImageState(fileName = null, width = 4, height = 3)
    }
}

/**
 * Mutable, in-memory block while a chapter is open; the editor's source of truth until it is saved.
 * [rich] holds the styled text and [value] the same text plus the field's selection; [setValue]
 * and [setRich] keep the two in step. For image blocks the text is the caption.
 * [headingLevel] applies to headings and [indent] to list items; both are kept across kind changes.
 */
@Stable
class EditorBlock(
    val id: String,
    kind: BlockKind,
    rich: RichText = RichText.Empty,
    selection: TextRange = TextRange(rich.text.length),
    align: TextAlignment = TextAlignment.START,
    headingLevel: Int = BlockContent.Heading.MIN_LEVEL,
    indent: Int = 0,
    image: ImageState? = null,
    table: TableState? = null,
) {
    var kind by mutableStateOf(kind)
    var align by mutableStateOf(align)
    var headingLevel by mutableIntStateOf(
        headingLevel.coerceIn(BlockContent.Heading.MIN_LEVEL, BlockContent.Heading.MAX_LEVEL),
    )
    var indent by mutableIntStateOf(indent.coerceIn(0, BlockContent.ListItem.MAX_INDENT))
    var rich by mutableStateOf(rich.normalized())
        private set
    var value by mutableStateOf(TextFieldValue(rich.text, selection))
        private set
    var image by mutableStateOf(image)
    var table by mutableStateOf(table)
    val focusRequester = FocusRequester()
    /** The field's last text layout, to scroll a search match into view; not state, it changes every frame. */
    var textLayout: TextLayoutResult? = null
    // One per table cell, made on first use so a focus request can name a cell before it is composed.
    private val cellFocusRequesters = mutableMapOf<CellKey, FocusRequester>()

    fun cellFocusRequester(cell: CellKey): FocusRequester = cellFocusRequesters.getOrPut(cell) { FocusRequester() }

    val isPendingImage: Boolean get() = kind == BlockKind.IMAGE && image?.fileName == null

    /** Takes an edit from the text field; styles follow the text, new text gets [typingStyle] when set. */
    fun setValue(newValue: TextFieldValue, typingStyle: SpanStyleData? = null) {
        if (newValue.text != value.text) {
            rich = RichTextEditor.applyTextChange(rich, newValue.text, typingStyle, newValue.selection.end)
        }
        value = newValue
    }

    fun setRich(newRich: RichText, selection: TextRange = TextRange(newRich.text.length)) {
        rich = newRich.normalized()
        // A pure restyle keeps the keyboard's composing region, so the word being typed stays in sync.
        value = if (newRich.text == value.text) value.copy(selection = selection) else TextFieldValue(newRich.text, selection)
    }

    fun setSelection(selection: TextRange) {
        value = value.copy(selection = selection)
    }

    fun toBlock(chapterId: String, position: Int) = Block(
        id = id,
        chapterId = chapterId,
        position = position,
        content = when (kind) {
            BlockKind.HEADING -> BlockContent.Heading(level = headingLevel, text = rich, align = align)
            BlockKind.PARAGRAPH -> BlockContent.Paragraph(text = rich, align = align)
            BlockKind.BULLET -> BlockContent.ListItem(ordered = false, indent = indent, text = rich, align = align)
            BlockKind.NUMBERED -> BlockContent.ListItem(ordered = true, indent = indent, text = rich, align = align)
            BlockKind.QUOTE -> BlockContent.Quote(text = rich, align = align)
            BlockKind.DIVIDER -> BlockContent.Divider
            BlockKind.PAGE_BREAK -> BlockContent.PageBreak
            BlockKind.TABLE -> {
                val table = checkNotNull(table)
                BlockContent.Table(rows = table.grid.rows, headerRow = table.headerRow)
            }
            BlockKind.IMAGE -> {
                val image = checkNotNull(image)
                BlockContent.Image(
                    fileName = checkNotNull(image.fileName) { "Pending images are not saved" },
                    width = image.width,
                    height = image.height,
                    caption = rich.text,
                    widthPercent = image.widthPercent,
                    align = image.align,
                )
            }
        },
    )

    companion object {
        fun from(block: Block): EditorBlock = when (val content = block.content) {
            is BlockContent.Heading -> EditorBlock(
                id = block.id,
                kind = BlockKind.HEADING,
                rich = content.text,
                align = content.align,
                headingLevel = content.level,
            )
            is BlockContent.Paragraph -> EditorBlock(block.id, BlockKind.PARAGRAPH, content.text, align = content.align)
            is BlockContent.ListItem -> EditorBlock(
                id = block.id,
                kind = if (content.ordered) BlockKind.NUMBERED else BlockKind.BULLET,
                rich = content.text,
                align = content.align,
                indent = content.indent,
            )
            is BlockContent.Quote -> EditorBlock(block.id, BlockKind.QUOTE, content.text, align = content.align)
            BlockContent.Divider -> EditorBlock(block.id, BlockKind.DIVIDER)
            BlockContent.PageBreak -> EditorBlock(block.id, BlockKind.PAGE_BREAK)
            is BlockContent.Table -> EditorBlock(block.id, BlockKind.TABLE, table = TableState.of(content))
            is BlockContent.Image -> EditorBlock(
                id = block.id,
                kind = BlockKind.IMAGE,
                rich = RichText(content.caption),
                image = ImageState(
                    fileName = content.fileName,
                    width = content.width,
                    height = content.height,
                    widthPercent = content.widthPercent,
                    align = content.align,
                ),
            )
        }
    }
}
